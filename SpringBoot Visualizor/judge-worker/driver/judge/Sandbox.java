package judge;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One sandboxed process: a bubblewrap sandbox started inside a transient systemd user scope.
 *
 * <ul>
 *   <li>The SCOPE gives kernel-enforced cgroup v2 limits (memory, swap, tasks, CPU) and a handle that kills every process in it,
 *       however they were started.</li>
 *   <li>BUBBLEWRAP gives empty namespaces (network, pid, ipc, uts, cgroup, user), a read-only minimal root, a size-limited tmpfs,
 *       no capabilities, no nested user namespaces, and the seccomp filter.</li>
 * </ul>
 * Output is read with hard limits: a learner cannot make the driver buffer more than {@code outputCap} bytes in total.
 */
final class Sandbox implements AutoCloseable {

    static final Path BASE = Path.of(System.getProperty("judge.base", System.getProperty("user.home") + "/springlab-judge"));

    private final String unit;
    private final Process process;
    private final OutputStream toSandbox;
    private final BlockingQueue<String> lines = new LinkedBlockingQueue<>();
    private final StringBuilder errorText = new StringBuilder();
    private final AtomicLong totalBytes = new AtomicLong();
    private final AtomicBoolean outputExceeded = new AtomicBoolean();
    private final long outputCap;
    private volatile boolean exited;
    private volatile long exitedAtNanos;

    static final String EOF = "\u0000EOF";

    Sandbox(String unit, Path srcDir, String profile, Limits limits, String harness) throws IOException {
        this.unit = unit;
        this.outputCap = limits.outputCapBytes();
        List<String> command = new ArrayList<>(List.of(
                "systemd-run", "--user", "--scope", "--quiet", "--collect", "--unit=" + unit,
                "-p", "MemoryMax=" + limits.memoryMax(), "-p", "MemorySwapMax=0", "-p", "TasksMax=" + limits.tasksMax(),
                "-p", "CPUQuota=" + limits.cpuQuotaPercent() + "%",
                "--", BASE.resolve("run-in-sandbox.sh").toString(), profile, srcDir.toString(),
                "/jdk/bin/java",
                "-Xmx" + limits.heapMb() + "m", "-Xss512k", "-XX:MaxDirectMemorySize=16m", "-XX:MaxMetaspaceSize=96m",
                "-XX:ReservedCodeCacheSize=32m", "-XX:+UseSerialGC", "-XX:TieredStopAtLevel=1", "-XX:-UsePerfData",
                "-XX:+ExitOnOutOfMemoryError", "-XX:+DisableAttachMechanism", "-Xshare:auto", "-Dfile.encoding=UTF-8",
                "-cp", "/judge/rt", "Launcher", harness));
        ProcessBuilder pb = new ProcessBuilder(command);
        // The driver's own environment is never passed on (the sandbox also starts with --clearenv). systemd-run needs the runtime dir.
        Map<String, String> env = pb.environment();
        String runtimeDir = env.get("XDG_RUNTIME_DIR");
        String path = env.get("PATH");
        env.clear();
        if (runtimeDir != null) {
            env.put("XDG_RUNTIME_DIR", runtimeDir);
        }
        env.put("PATH", path == null ? "/usr/bin:/bin" : path);
        this.process = pb.start();
        this.toSandbox = process.getOutputStream();
        startReader(process.getInputStream(), true);
        startReader(process.getErrorStream(), false);
        Thread waiter = new Thread(() -> {
            try {
                process.waitFor();
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            exited = true;
            exitedAtNanos = System.nanoTime();
        }, "sandbox-wait-" + unit);
        waiter.setDaemon(true);
        waiter.start();
    }

    private void startReader(InputStream in, boolean protocol) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[4096];
            StringBuilder line = new StringBuilder();
            boolean tooLong = false;
            try {
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (totalBytes.addAndGet(n) > outputCap) {
                        outputExceeded.set(true);
                        // keep draining so the sandbox is not blocked on a full pipe before we kill it, but keep nothing
                        continue;
                    }
                    String chunk = new String(buf, 0, n, StandardCharsets.UTF_8);
                    if (!protocol) {
                        synchronized (errorText) {
                            if (errorText.length() < 8192) {
                                errorText.append(chunk, 0, Math.min(chunk.length(), 8192 - errorText.length()));
                            }
                        }
                        continue;
                    }
                    for (int i = 0; i < chunk.length(); i++) {
                        char c = chunk.charAt(i);
                        if (c == '\n') {
                            lines.add(tooLong ? "" : line.toString());
                            line.setLength(0);
                            tooLong = false;
                        } else if (line.length() < 16_384) {
                            line.append(c);
                        } else {
                            tooLong = true;
                        }
                    }
                }
            } catch (IOException ignored) {
                // stream closed by the kill
            } finally {
                if (protocol) {
                    lines.add(EOF);
                }
            }
        }, "sandbox-read-" + unit + (protocol ? "-out" : "-err"));
        t.setDaemon(true);
        t.start();
    }

    void write(String line) throws IOException {
        toSandbox.write((line + "\n").getBytes(StandardCharsets.UTF_8));
        toSandbox.flush();
    }

    /** Next stdout line, EOF marker, or null on timeout. */
    String poll(long millis) throws InterruptedException {
        return lines.poll(millis, TimeUnit.MILLISECONDS);
    }

    boolean outputExceeded() {
        return outputExceeded.get();
    }

    String errorOutput() {
        synchronized (errorText) {
            return errorText.toString();
        }
    }

    boolean exited() {
        return exited;
    }

    int exitCode() {
        return exited ? process.exitValue() : -1;
    }

    String unit() {
        return unit;
    }

    /** Kills every process of the scope (not only the direct child), then makes sure nothing is left. */
    void kill() {
        run("systemctl", "--user", "kill", "--signal=SIGKILL", "--kill-whom=all", unit + ".scope");
        process.destroyForcibly();
        try {
            process.waitFor(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        run("systemctl", "--user", "stop", unit + ".scope");
    }

    /** True while the scope still exists (and so may still hold processes). */
    boolean scopeAlive() {
        return run("systemctl", "--user", "is-active", "--quiet", unit + ".scope") == 0;
    }

    private static int run(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            p.getInputStream().readAllBytes();
            if (!p.waitFor(10, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return -2;
            }
            return p.exitValue();
        } catch (Exception e) {
            return -3;
        }
    }

    @Override
    public void close() {
        if (!exited || scopeAlive()) {
            kill();
        }
    }
}
