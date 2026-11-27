package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * Runs the standalone judge-worker inside Ubuntu WSL (bubblewrap sandbox in a systemd user scope), from outside the SpringLab JVM.
 *
 * <p>How a job is run: this class starts {@code wsl.exe --exec <jdk>/bin/java -cp <worker> judge.Main submit|run --framed --job <id> -},
 * writes the source to its stdin as {@code LEN n} plus n bytes, and keeps stdin OPEN. It then reads one JSON line from stdout. If this
 * process goes away (a restart, a crash, a kill), stdin closes and the worker kills the sandbox by itself. {@link #cancel} kills the
 * sandbox scope through a second worker command and then the client process.
 *
 * <p>Everything on the command line comes from validated configuration and a generated job id. Nothing comes from a request, and
 * nothing passes through a shell ({@code --exec}). The learner source travels only on stdin. The client gets a minimal environment.
 */
public final class WslBackend implements ExecutionBackend {

    public static final String NAME = "wsl";

    private static final Logger log = LoggerFactory.getLogger(WslBackend.class);
    private static final Pattern DISTRO = Pattern.compile("[A-Za-z0-9._-]{1,40}");
    private static final Pattern BASE_DIR = Pattern.compile("/[A-Za-z0-9._/-]{1,200}");
    private static final Pattern EXECUTABLE = Pattern.compile("[A-Za-z0-9._:\\\\/ -]{1,200}");
    private static final Pattern JOB_ID = Pattern.compile("[0-9a-f]{16}");
    private static final int MAX_RESPONSE_BYTES = 256 * 1024;
    private static final Duration CHECK_TIMEOUT = Duration.ofSeconds(60);
    private static final Duration CANCEL_TIMEOUT = Duration.ofSeconds(30);
    private static final long UP_TTL_MILLIS = 30_000;
    private static final long DOWN_TTL_MILLIS = 10_000;

    private record Cached(Availability availability, long atMillis) {
    }

    private final String executable;
    private final String distro;
    private final String baseDir;
    private final String configError;
    private final WorkerLauncher launcher;
    private final WorkerResponseParser parser;
    private final Map<String, Process> running = new ConcurrentHashMap<>();
    private volatile Cached cached;

    public WslBackend(ExecutionProperties.Wsl config, ObjectMapper mapper) {
        this(config, WorkerLauncher.processBuilder(), new WorkerResponseParser(mapper));
    }

    WslBackend(ExecutionProperties.Wsl config, WorkerLauncher launcher, WorkerResponseParser parser) {
        this.launcher = launcher;
        this.parser = parser;
        this.executable = config == null || config.executable() == null ? "" : config.executable();
        this.distro = config == null || config.distro() == null ? "" : config.distro();
        this.baseDir = config == null || config.baseDir() == null ? "" : config.baseDir();
        this.configError = validate();
    }

    private String validate() {
        if (distro.isEmpty() || baseDir.isEmpty()) {
            return "The WSL backend needs springlab.coding.execution.wsl.distro and base-dir";
        }
        if (!EXECUTABLE.matcher(executable).matches()) {
            return "springlab.coding.execution.wsl.executable is not acceptable";
        }
        if (!DISTRO.matcher(distro).matches() || distro.startsWith("-")) {
            return "springlab.coding.execution.wsl.distro is not acceptable";
        }
        if (!BASE_DIR.matcher(baseDir).matches() || baseDir.contains("..") || baseDir.contains("//") || baseDir.endsWith("/")) {
            return "springlab.coding.execution.wsl.base-dir is not acceptable";
        }
        return null;
    }

    @Override
    public String name() {
        return NAME;
    }

    /** The command that starts a worker subcommand inside WSL. Fixed parts only. */
    List<String> command(String... workerArguments) {
        List<String> c = new java.util.ArrayList<>(List.of(executable, "-d", distro, "--exec", baseDir + "/jdk17/bin/java",
                "-Djudge.base=" + baseDir, "-cp", baseDir + "/build/driver", "judge.Main"));
        c.addAll(List.of(workerArguments));
        return List.copyOf(c);
    }

    @Override
    public Availability availability() {
        if (configError != null) {
            return Availability.down(NAME, configError);
        }
        Cached c = cached;
        long now = System.currentTimeMillis();
        if (c != null && now - c.atMillis() < (c.availability().available() ? UP_TTL_MILLIS : DOWN_TTL_MILLIS)) {
            return c.availability();
        }
        Availability fresh = probe();
        cached = new Cached(fresh, System.currentTimeMillis());
        return fresh;
    }

    private Availability probe() {
        Process p = null;
        try {
            p = launcher.start(command("check"));
            p.getOutputStream().close();
            ByteHolder answer = new ByteHolder();
            Thread reader = startReader(p.getInputStream(), 4096, answer, "worker-check-out");
            startReader(p.getErrorStream(), 4096, new ByteHolder(), "worker-check-err");
            if (!p.waitFor(CHECK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                destroyTree(p);
                return Availability.down(NAME, "WSL or the judge worker did not answer in time");
            }
            reader.join(2_000);
            byte[] out = answer.bytes;
            String text = new String(out, StandardCharsets.UTF_8).trim();
            if (p.exitValue() == 0 && text.equals("{\"ok\":true}")) {
                return Availability.up(NAME);
            }
            if (p.exitValue() == 0 && text.startsWith("{\"ok\":false,\"reason\":\"") && text.endsWith("\"}") && text.length() < 200) {
                return Availability.down(NAME, "The judge worker reports: " + text.substring(22, text.length() - 2));
            }
            return Availability.down(NAME, "WSL, the JDK or the judge worker is not available (exit " + p.exitValue() + ")");
        } catch (IOException e) {
            return Availability.down(NAME, "WSL could not be started");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Availability.down(NAME, "The availability check was interrupted");
        } finally {
            if (p != null && p.isAlive()) {
                destroyTree(p);
            }
        }
    }

    @Override
    public ExecutionResult execute(String jobId, ExecutionRequest request, Duration hardTimeout) throws InterruptedException {
        if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
            throw new IllegalArgumentException("invalid job id");
        }
        if (configError != null) {
            throw new ExecutionUnavailableException("Code execution is not available");
        }
        long started = System.nanoTime();
        byte[] source = request.source();
        Process p;
        try {
            p = launcher.start(command(request.mode() == ExecutionRequest.Mode.SUBMIT ? "submit" : "run", "--framed", "--harness", request.problem().name(), "--job", jobId, "-"));
        } catch (IOException e) {
            log.warn("job {}: the worker client could not be started", jobId);
            return ExecutionResult.of(Verdict.INTERNAL_ERROR, elapsed(started));
        }
        running.put(jobId, p);
        try {
            ByteHolder stdout = new ByteHolder();
            Thread reader = startReader(p.getInputStream(), MAX_RESPONSE_BYTES, stdout, "worker-out-" + jobId);
            Thread drain = startReader(p.getErrorStream(), 16 * 1024, new ByteHolder(), "worker-err-" + jobId);
            try {
                OutputStream in = p.getOutputStream();
                in.write(("LEN " + source.length + "\n").getBytes(StandardCharsets.US_ASCII));
                in.write(source);
                // the tests come from the backend's database; the worker's driver keeps the expected values out of the sandbox
                StringBuilder tests = new StringBuilder("TESTS ").append(request.tests().size()).append('\n');
                for (int i = 0; i < request.tests().size(); i++) {
                    tests.append(request.tests().get(i).toWireLine(i)).append('\n');
                }
                in.write(tests.toString().getBytes(StandardCharsets.US_ASCII));
                in.flush(); // stdin stays open on purpose: closing it tells the worker the caller is gone
            } catch (IOException e) {
                // the client died before reading the source; the exit code below decides
            }
            boolean exited = p.waitFor(hardTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!exited) {
                log.warn("job {}: hard timeout, killing the sandbox", jobId);
                cancelScope(jobId);
                destroyTree(p);
                return ExecutionResult.of(Verdict.TIME_LIMIT, elapsed(started));
            }
            reader.join(5_000);
            drain.join(1_000);
            if (p.exitValue() != 0) {
                log.warn("job {}: the worker exited with status {}", jobId, p.exitValue());
                return ExecutionResult.of(Verdict.INTERNAL_ERROR, elapsed(started));
            }
            if (stdout.overflow || stdout.bytes.length == 0) {
                log.warn("job {}: the worker answer was {}", jobId, stdout.overflow ? "too large" : "empty");
                return ExecutionResult.of(Verdict.INTERNAL_ERROR, elapsed(started));
            }
            try {
                return parser.parse(new String(stdout.bytes, StandardCharsets.UTF_8).trim());
            } catch (WorkerResponseParser.MalformedResponseException e) {
                log.warn("job {}: malformed worker answer ({})", jobId, e.getMessage());
                return ExecutionResult.of(Verdict.INTERNAL_ERROR, elapsed(started));
            }
        } catch (InterruptedException e) {
            cancelScope(jobId);
            destroyTree(p);
            throw e;
        } finally {
            running.remove(jobId);
            closeQuietly(p.getOutputStream());
            if (p.isAlive()) {
                destroyTree(p);
            }
        }
    }

    @Override
    public void cancel(String jobId) {
        if (jobId == null || !JOB_ID.matcher(jobId).matches()) {
            return;
        }
        Process p = running.get(jobId);
        if (p == null) {
            return;
        }
        cancelScope(jobId);
        destroyTree(p);
    }

    /** Asks the worker to kill the sandbox scope of the job (every process in it). Best effort. */
    private void cancelScope(String jobId) {
        Process c = null;
        try {
            c = launcher.start(command("cancel", jobId));
            c.getOutputStream().close();
            startReader(c.getInputStream(), 1024, new ByteHolder(), "worker-cancel-out");
            startReader(c.getErrorStream(), 1024, new ByteHolder(), "worker-cancel-err");
            if (!c.waitFor(CANCEL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                destroyTree(c);
            }
        } catch (IOException e) {
            log.warn("job {}: the cancel command could not be started", jobId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (c != null && c.isAlive()) {
                destroyTree(c);
            }
        }
    }

    private static final class ByteHolder {
        volatile byte[] bytes = new byte[0];
        volatile boolean overflow;
    }

    private static Thread startReader(InputStream in, int limit, ByteHolder into, String name) {
        Thread t = new Thread(() -> {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                byte[] chunk = new byte[4096];
                int n;
                while ((n = in.read(chunk)) > 0) {
                    if (buffer.size() + n > limit) {
                        into.overflow = true; // keep draining so the process is never blocked, keep nothing more
                        continue;
                    }
                    buffer.write(chunk, 0, n);
                }
                into.bytes = buffer.toByteArray();
            } catch (IOException ignored) {
                // closed by a kill
            }
        }, name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void destroyTree(Process p) {
        p.descendants().forEach(ProcessHandle::destroyForcibly);
        p.destroyForcibly();
    }

    private static void closeQuietly(OutputStream out) {
        try {
            out.close();
        } catch (IOException ignored) {
            // already closed
        }
    }

    private static long elapsed(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000L;
    }
}
