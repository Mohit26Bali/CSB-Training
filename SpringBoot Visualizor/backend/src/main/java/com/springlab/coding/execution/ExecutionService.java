package com.springlab.coding.execution;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Admission control and scheduling for learner code. It owns no sandbox: it decides WHETHER and WHEN a job may start and hands the
 * job to an {@link ExecutionBackend}.
 *
 * <ul>
 *   <li><b>One in-flight job per user</b>, and <b>a bounded total</b> of {@code maxWorkers} running plus {@code queueCapacity} waiting.
 *       Both rules are checked and the job is registered under ONE lock, so two racing requests cannot both slip through.</li>
 *   <li>A refused submission is refused <i>before</i> it takes a slot (source invalid: 400, backend unavailable: 503) or without
 *       leaving anything behind (same user busy: 409, no capacity: 429).</li>
 *   <li>Jobs are owner-scoped: another user's job id behaves exactly like an unknown id.</li>
 *   <li>State is in memory only. After a restart no job is known, so nothing can be reported as accepted that this process did not
 *       judge; the worker kills an orphaned sandbox itself when its caller disappears.</li>
 *   <li>A cancelled job never reports what the sandbox returned, even if the backend returned ACCEPTED a moment later.</li>
 * </ul>
 */
@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int RETAINED_FINISHED_JOBS = 200;

    private static final class Job {
        final String id;
        final String user;
        final ExecutionRequest.Mode mode;
        final Instant submittedAt = Instant.now();
        ExecutionRequest request;
        volatile ExecutionState state = ExecutionState.QUEUED;
        volatile ExecutionResult result;
        volatile boolean cancelRequested;
        volatile Thread worker;
        Future<?> future;

        Job(String id, String user, ExecutionRequest request) {
            this.id = id;
            this.user = user;
            this.mode = request.mode();
            this.request = request;
        }
    }

    private final ExecutionBackend backend;
    private final Duration hardTimeout;
    private final int maxWorkers;
    private final int queueCapacity;
    private final ThreadPoolExecutor executor;
    private final ReentrantLock lock = new ReentrantLock();
    private final Map<String, String> inFlightByUser = new HashMap<>();
    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger running = new AtomicInteger();
    private final AtomicInteger peakRunning = new AtomicInteger();
    private boolean shuttingDown;
    private volatile String lastUnavailableReason;

    public ExecutionService(ExecutionBackend backend, ExecutionProperties properties) {
        this.backend = backend;
        this.maxWorkers = properties.maxWorkers();
        this.queueCapacity = properties.queueCapacity();
        this.hardTimeout = Duration.ofSeconds(properties.hardTimeoutSeconds());
        // No thread is created until the first job. The queue bound equals the admission bound, so the executor never rejects.
        this.executor = new ThreadPoolExecutor(maxWorkers, maxWorkers, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(queueCapacity),
                runnable -> {
                    Thread t = new Thread(runnable, "code-runner");
                    t.setDaemon(true);
                    return t;
                });
        log.info("Coding execution backend: {} (workers {}, queue {})", backend.name(), maxWorkers, queueCapacity);
    }

    /** Whether code can be run right now. For internal use. */
    public Availability availability() {
        return backend.availability();
    }

    /**
     * Validates, checks the backend, and admits the job. Returns the job id.
     *
     * @throws InvalidSourceException         400, the source is not acceptable (nothing is consumed)
     * @throws ExecutionUnavailableException  503, there is no safe backend (nothing is consumed)
     * @throws ExecutionBusyException         409, this user already has a job waiting or running
     * @throws ExecutionCapacityException     429, all workers are busy and the queue is full
     */
    public String submit(String user, ExecutionRequest request) {
        if (user == null || user.isBlank()) {
            throw new IllegalArgumentException("user is required");
        }
        SourceValidator.validate(request.source());
        Availability availability = backend.availability();
        if (!availability.available()) {
            // for the operator only: the learner is told nothing about the host. Logged once per distinct reason, not once per request.
            if (!availability.reason().equals(lastUnavailableReason)) {
                lastUnavailableReason = availability.reason();
                log.warn("Code execution is unavailable ({}): {}", availability.backend(), availability.reason());
            }
            throw new ExecutionUnavailableException("Code execution is not available right now");
        }
        lastUnavailableReason = null;
        Job job;
        lock.lock();
        try {
            if (shuttingDown) {
                throw new ExecutionUnavailableException("Code execution is not available right now");
            }
            if (inFlightByUser.containsKey(user)) {
                throw new ExecutionBusyException();
            }
            if (inFlight.get() >= maxWorkers + queueCapacity) {
                throw new ExecutionCapacityException();
            }
            job = new Job(newJobId(), user, request);
            inFlightByUser.put(user, job.id);
            inFlight.incrementAndGet();
            jobs.put(job.id, job);
            try {
                job.future = executor.submit(() -> run(job));
            } catch (RuntimeException rejected) {
                // cannot happen while the bounds agree, but if it ever does the slot is given back and the caller is told to retry
                release(job);
                jobs.remove(job.id);
                throw new ExecutionCapacityException();
            }
        } finally {
            lock.unlock();
        }
        log.info("execution job={} user={} mode={} sourceBytes={} sourceSha256={}", job.id, user, request.mode(), request.sourceLength(),
                sha256Prefix(request.source()));
        return job.id;
    }

    private void run(Job job) {
        lock.lock();
        try {
            if (job.state != ExecutionState.QUEUED) {
                return; // cancelled while waiting
            }
            job.state = ExecutionState.RUNNING;
            job.worker = Thread.currentThread();
        } finally {
            lock.unlock();
        }
        int now = running.incrementAndGet();
        peakRunning.accumulateAndGet(now, Math::max);
        ExecutionResult outcome;
        try {
            if (job.cancelRequested) {
                throw new InterruptedException("cancelled before it started");
            }
            outcome = backend.execute(job.id, job.request, hardTimeout);
            if (outcome == null) {
                outcome = ExecutionResult.of(Verdict.INTERNAL_ERROR);
            }
        } catch (InterruptedException e) {
            outcome = ExecutionResult.of(Verdict.CANCELLED);
            Thread.interrupted();
        } catch (ExecutionRejectedException e) {
            outcome = ExecutionResult.of(Verdict.INTERNAL_ERROR);
        } catch (Throwable t) {
            log.warn("execution job={} failed in the backend: {}", job.id, t.getClass().getSimpleName());
            outcome = ExecutionResult.of(Verdict.INTERNAL_ERROR);
        } finally {
            running.decrementAndGet();
        }
        lock.lock();
        try {
            Thread.interrupted(); // a late cancel must not leak into the pool thread
            job.worker = null;
            if (job.cancelRequested || job.state == ExecutionState.CANCELLED) {
                job.state = ExecutionState.CANCELLED;
                job.result = ExecutionResult.of(Verdict.CANCELLED);
            } else {
                job.state = ExecutionState.FINISHED;
                job.result = outcome;
            }
            job.request = null;
            release(job);
            trim();
        } finally {
            lock.unlock();
        }
    }

    /** The caller's own job, or empty for an unknown id or someone else's. */
    public Optional<ExecutionStatus> status(String user, String jobId) {
        lock.lock();
        try {
            Job job = jobs.get(jobId);
            if (job == null || !job.user.equals(user)) {
                return Optional.empty();
            }
            return Optional.of(new ExecutionStatus(job.id, job.state, job.result, job.submittedAt));
        } finally {
            lock.unlock();
        }
    }

    /**
     * Cancels the caller's own job. A waiting job leaves the queue at once; a running job has its whole sandbox killed and its slot
     * comes back when the worker thread has finished. Empty for an unknown id or someone else's.
     */
    public Optional<ExecutionStatus> cancel(String user, String jobId) {
        Job job;
        boolean wasRunning;
        lock.lock();
        try {
            job = jobs.get(jobId);
            if (job == null || !job.user.equals(user)) {
                return Optional.empty();
            }
            if (job.state == ExecutionState.QUEUED) {
                job.state = ExecutionState.CANCELLED;
                job.result = ExecutionResult.of(Verdict.CANCELLED);
                job.cancelRequested = true;
                if (job.future != null) {
                    job.future.cancel(false);
                    executor.remove((Runnable) job.future);
                }
                job.request = null;
                release(job);
                return Optional.of(new ExecutionStatus(job.id, job.state, job.result, job.submittedAt));
            }
            if (job.state != ExecutionState.RUNNING) {
                return Optional.of(new ExecutionStatus(job.id, job.state, job.result, job.submittedAt));
            }
            job.cancelRequested = true;
            wasRunning = true;
        } finally {
            lock.unlock();
        }
        if (wasRunning) {
            backend.cancel(job.id);
            Thread w = job.worker;
            if (w != null) {
                w.interrupt();
            }
        }
        return status(user, jobId);
    }

    /** Gives back the user's slot and the total slot. Call with the lock held, exactly once per job. */
    private void release(Job job) {
        if (inFlightByUser.remove(job.user, job.id)) {
            inFlight.decrementAndGet();
        }
    }

    private void trim() {
        int finished = 0;
        for (Job j : jobs.values()) {
            if (j.state == ExecutionState.FINISHED || j.state == ExecutionState.CANCELLED) {
                finished++;
            }
        }
        for (Iterator<Job> it = jobs.values().iterator(); finished > RETAINED_FINISHED_JOBS && it.hasNext(); ) {
            Job j = it.next();
            if (j.state == ExecutionState.FINISHED || j.state == ExecutionState.CANCELLED) {
                it.remove();
                finished--;
            }
        }
    }

    @PreDestroy
    void shutdown() {
        List<Job> unfinished = new ArrayList<>();
        lock.lock();
        try {
            shuttingDown = true;
            for (Job j : jobs.values()) {
                if (j.state == ExecutionState.QUEUED || j.state == ExecutionState.RUNNING) {
                    j.cancelRequested = true;
                    unfinished.add(j);
                }
            }
        } finally {
            lock.unlock();
        }
        for (Job j : unfinished) {
            backend.cancel(j.id);
        }
        ExecutorService pool = executor;
        pool.shutdownNow();
        try {
            pool.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ---- figures for internal reporting and tests

    public int runningCount() {
        return running.get();
    }

    public int inFlightCount() {
        return inFlight.get();
    }

    /** The highest number of jobs that ever ran at the same time. */
    public int peakRunning() {
        return peakRunning.get();
    }

    private static String newJobId() {
        byte[] b = new byte[8];
        RANDOM.nextBytes(b);
        return HexFormat.of().formatHex(b);
    }

    private static String sha256Prefix(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)).substring(0, 12);
        } catch (NoSuchAlgorithmException e) {
            return "?";
        }
    }

    /** Test hook: a plain description that never contains learner code. */
    @Override
    public String toString() {
        return "ExecutionService[backend=" + backend.name() + ", inFlight=" + inFlight.get() + ", running=" + running.get() + "]";
    }
}
