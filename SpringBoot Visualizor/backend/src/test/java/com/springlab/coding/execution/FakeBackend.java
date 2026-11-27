package com.springlab.coding.execution;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/** A controllable backend for the scheduling tests: jobs block until released, and the test sees exactly what the service asked for. */
final class FakeBackend implements ExecutionBackend {

    volatile Availability availability = Availability.up("fake");
    volatile CountDownLatch release = new CountDownLatch(1);
    volatile boolean ignoreInterrupt;
    volatile Supplier<ExecutionResult> result = () -> accepted();
    volatile RuntimeException failWith;
    final AtomicInteger started = new AtomicInteger();
    final AtomicInteger concurrent = new AtomicInteger();
    final AtomicInteger maxConcurrent = new AtomicInteger();
    final Set<String> executed = ConcurrentHashMap.newKeySet();
    final Set<String> cancelled = Collections.synchronizedSet(new java.util.LinkedHashSet<>());

    static ExecutionResult accepted() {
        return new ExecutionResult(Verdict.ACCEPTED, java.util.List.of(new ExecutionResult.TestOutcome("t", true, "PASS", null)), 1, 1, "", 5, -1, -1);
    }

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public Availability availability() {
        return availability;
    }

    @Override
    public ExecutionResult execute(String jobId, ExecutionRequest request, Duration hardTimeout) throws InterruptedException {
        started.incrementAndGet();
        executed.add(jobId);
        int now = concurrent.incrementAndGet();
        maxConcurrent.accumulateAndGet(now, Math::max);
        try {
            CountDownLatch gate = release;
            try {
                gate.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                if (!ignoreInterrupt) {
                    throw e;
                }
                gate.await(60, TimeUnit.SECONDS); // a backend that does not stop on cancel: it returns its result anyway, later
            }
            if (failWith != null) {
                throw failWith;
            }
            return result.get();
        } finally {
            concurrent.decrementAndGet();
        }
    }

    @Override
    public void cancel(String jobId) {
        cancelled.add(jobId);
    }

    void open() {
        release.countDown();
    }
}
