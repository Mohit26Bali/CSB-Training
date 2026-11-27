package judge;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Bounded adversarial tests for the sandbox. Each attack is a learner submission that tries ONE thing; the suite checks the verdict
 * and, from what the attack reports on stderr ("ATTACK ..." lines, visible because the suite runs with debug output), that the
 * forbidden operation really was refused. Every attack is bounded (loops with fixed limits), and the kernel limits are the real
 * protection: the suite never relies on the attack stopping by itself.
 *
 * Run inside Ubuntu WSL:  java -cp build/driver:build/suite judge.AdversarialSuite
 */
public final class AdversarialSuite {

    private static final String SENTINEL_TAIL = "8f3a91";
    private static final String SENTINEL = "HIDDEN-SENTINEL-" + SENTINEL_TAIL;
    private static final String ENV_SENTINEL = "ENV-SENTINEL-77c1d0";
    private static final Path BASE = Sandbox.BASE;

    private static final String TEMPLATE = """
            import java.util.concurrent.Callable;
            import java.io.*;
            import java.net.*;
            import java.nio.*;
            import java.nio.file.*;
            import java.util.*;

            public final class RetryExecutor {
                static boolean done;
                static void say(String s) { System.err.println("ATTACK " + s); }
                static String run(String... cmd) {
                    try {
                        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
                        String out = new String(p.getInputStream().readAllBytes());
                        p.waitFor();
                        return out;
                    } catch (Throwable t) { return "exec failed: " + t.getClass().getSimpleName() + ": " + t.getMessage(); }
                }
                //MEMBERS//
                public static <T> T execute(Callable<T> action, int maxAttempts, long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) throws Exception {
                    if (!done) {
                        done = true;
                        //BODY//
                    }
                    return action.call();
                }
            }
            """;

    private static final String REFERENCE = """
            import java.util.concurrent.Callable;
            public final class RetryExecutor {
                public static <T> T execute(Callable<T> action, int maxAttempts, long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) throws Exception {
                    if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be at least 1");
                    long delay = Math.min(baseDelayMillis, maxDelayMillis);
                    for (int attempt = 1; ; attempt++) {
                        try { return action.call(); }
                        catch (TransientException e) {
                            if (attempt == maxAttempts) throw e;
                            sleeper.sleep(delay);
                            delay = delay > maxDelayMillis / 2 ? maxDelayMillis : delay * 2;
                        }
                    }
                }
            }
            """;

    record Case(String name, String profile, String source, Limits limits, Predicate<Judge.Result> check, String expectation) {
    }

    private static final List<String> report = new ArrayList<>();
    private static final AtomicInteger failures = new AtomicInteger();

    private static void record(String name, boolean ok, String detail) {
        String line = (ok ? "PASS  " : "FAIL  ") + name + " :: " + detail;
        synchronized (report) {
            report.add(line);
        }
        if (!ok) {
            failures.incrementAndGet();
        }
        System.out.println(line);
    }

    private static String attack(String members, String body) {
        return TEMPLATE.replace("//MEMBERS//", members).replace("//BODY//", body);
    }

    private static boolean has(Judge.Result r, String text) {
        return r.learnerOutput().contains(text);
    }

    private static int count(String text, String needle) {
        int n = 0;
        for (int i = text.indexOf(needle); i >= 0; i = text.indexOf(needle, i + 1)) {
            n++;
        }
        return n;
    }

    private static Judge.Result judge(String source, String profile, Limits limits) {
        return Judge.judge(source.getBytes(StandardCharsets.UTF_8), true, profile, limits);
    }

    private static boolean isOneOf(Judge.Result r, Judge.Verdict... vs) {
        for (Judge.Verdict v : vs) {
            if (r.verdict() == v) {
                return true;
            }
        }
        return false;
    }

    private static String sh(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes()).trim();
        p.waitFor();
        return out;
    }

    public static void main(String[] args) throws Exception {
        Limits lim = Limits.defaults();
        Files.createDirectories(BASE.resolve("jobs"));
        Files.writeString(BASE.resolve("secret-sentinel.txt"), SENTINEL + "\n");
        String memBefore = sh("free", "-m");

        // ---- network: a real listener on the WSL host, reachable on every host address
        ServerSocket listener = new ServerSocket(0);
        AtomicInteger accepted = new AtomicInteger();
        Thread accept = new Thread(() -> {
            try {
                while (true) {
                    Socket s = listener.accept();
                    accepted.incrementAndGet();
                    s.close();
                }
            } catch (IOException ignored) {
                // closed at the end
            }
        });
        accept.setDaemon(true);
        accept.start();
        List<String> hosts = new ArrayList<>(List.of("127.0.0.1", "localhost"));
        for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            for (InetAddress a : Collections.list(n.getInetAddresses())) {
                if (a.getAddress().length == 4 && !hosts.contains(a.getHostAddress())) {
                    hosts.add(a.getHostAddress());
                }
            }
        }
        String gateway = sh("bash", "-c", "ip route 2>/dev/null | awk '/default/ {print $3; exit}'");
        if (!gateway.isEmpty()) {
            hosts.add(gateway); // the Windows host as seen from WSL
        }

        // ============================================================ functional correctness through the sandbox
        Judge.Result good = judge(REFERENCE, "strict", lim);
        record("A01 reference solution is accepted", good.verdict() == Judge.Verdict.ACCEPTED && good.passed() == 12,
                good.verdict() + " " + good.passed() + "/" + good.total() + " in " + good.wallMillis() + " ms, peak " + good.peakKb() + " KB, cpu " + good.cpuMillis() + " ms");

        String wrong = REFERENCE.replace("delay = delay > maxDelayMillis / 2 ? maxDelayMillis : delay * 2;", "delay = delay * 2;")
                .replace("if (attempt == maxAttempts) throw e;\n                            sleeper.sleep(delay);", "sleeper.sleep(delay); if (attempt == maxAttempts) throw e;");
        Judge.Result w = judge(wrong, "strict", lim);
        boolean hiddenSilent = w.tests().stream().filter(t -> !t.visible()).allMatch(t -> t.message() == null);
        boolean visibleExplains = w.tests().stream().filter(t -> t.visible() && t.status().equals("FAIL")).allMatch(t -> t.message() != null);
        record("A02 wrong solution: WRONG_ANSWER, hidden tests give status only, visible failures explain", w.verdict() == Judge.Verdict.WRONG_ANSWER && hiddenSilent && visibleExplains,
                w.verdict() + " " + w.passed() + "/" + w.total() + " hiddenMessagesNull=" + hiddenSilent);

        Judge.Result ce = judge(REFERENCE.replace("return action.call();", "return action.call()"), "strict", lim);
        record("A03 compile error is reported without host paths", ce.verdict() == Judge.Verdict.COMPILE_ERROR && ce.compilerOutput().contains("RetryExecutor.java:")
                && !ce.compilerOutput().contains("/src/") && !ce.compilerOutput().contains("/tmp/"), ce.compilerOutput().replace('\n', ' '));

        Judge.Result extra = judge(REFERENCE + "\nclass Helper {}\n", "strict", lim);
        record("A04 an extra top-level class is rejected", extra.verdict() == Judge.Verdict.COMPILE_ERROR && extra.compilerOutput().contains("Only the class RetryExecutor"), extra.compilerOutput().trim());

        Judge.Result sig = judge(REFERENCE.replace("long maxDelayMillis,", "int maxDelayMillis,").replace("delay > maxDelayMillis / 2 ? maxDelayMillis", "delay > maxDelayMillis / 2 ? (long) maxDelayMillis"), "strict", lim);
        record("A05 a wrong method signature is a compile error, not a crash", sig.verdict() == Judge.Verdict.COMPILE_ERROR, sig.verdict() + " " + sig.compilerOutput().replace('\n', ' '));

        String nest = "(".repeat(3000) + "1" + ")".repeat(3000);
        Judge.Result bomb = judge(REFERENCE.replace("long delay = Math.min(baseDelayMillis, maxDelayMillis);", "long delay = " + nest + " * 0 + Math.min(baseDelayMillis, maxDelayMillis);"), "strict", lim);
        record("A06 compile bomb (3000 nested parentheses) is contained", isOneOf(bomb, Judge.Verdict.COMPILE_ERROR, Judge.Verdict.TIME_LIMIT, Judge.Verdict.RUNTIME_ERROR, Judge.Verdict.MEMORY_LIMIT, Judge.Verdict.OUTPUT_LIMIT) && bomb.wallMillis() < lim.totalWallMillis() + 5000,
                bomb.verdict() + " in " + bomb.wallMillis() + " ms");

        // ============================================================ CPU and time
        Judge.Result loop = judge(attack("", "while (true) { }"), "strict", lim);
        record("B01 infinite loop: TIME_LIMIT, killed", loop.verdict() == Judge.Verdict.TIME_LIMIT && loop.wallMillis() < 12_000, loop.verdict() + " in " + loop.wallMillis() + " ms");
        Judge.Result initLoop = judge(attack("static { if (System.nanoTime() > 0) { while (true) { } } }", ""), "strict", lim);
        record("B02 infinite loop in a static initializer: TIME_LIMIT", initLoop.verdict() == Judge.Verdict.TIME_LIMIT, initLoop.verdict() + " in " + initLoop.wallMillis() + " ms");
        Judge.Result sleepy = judge(attack("", "Thread.sleep(Long.MAX_VALUE);"), "strict", lim);
        record("B03 sleeping forever: TIME_LIMIT", sleepy.verdict() == Judge.Verdict.TIME_LIMIT, sleepy.verdict() + " in " + sleepy.wallMillis() + " ms");
        Judge.Result stdin = judge(attack("", "System.err.println(new FileInputStream(FileDescriptor.in).read());"), "strict", lim);
        record("B04 reading the protocol stdin directly does not yield an ACCEPTED verdict", stdin.verdict() != Judge.Verdict.ACCEPTED, stdin.verdict() + " in " + stdin.wallMillis() + " ms");
        Judge.Result cpuBurn = judge(attack("static volatile long sink;", "long end = System.nanoTime() + 2_000_000_000L; Thread[] ts = new Thread[8]; for (int i = 0; i < 8; i++) { ts[i] = new Thread(() -> { while (System.nanoTime() < end) sink++; }); ts[i].start(); } for (Thread t : ts) t.join();"), "strict", lim);
        record("B05 eight busy threads for 2 s of wall time: CPU is capped at about one core by the cgroup (uncapped would use 16 s+)",
                cpuBurn.cpuMillis() > 0 && cpuBurn.cpuMillis() < 9_000, cpuBurn.verdict() + " wall " + cpuBurn.wallMillis() + " ms, cpu " + cpuBurn.cpuMillis() + " ms (includes about 2 s of compilation)");

        // ============================================================ memory
        Judge.Result heap = judge(attack("", "List<byte[]> l = new ArrayList<>(); while (true) l.add(new byte[1 << 20]);"), "strict", lim);
        record("C01 heap bomb: MEMORY_LIMIT", heap.verdict() == Judge.Verdict.MEMORY_LIMIT, heap.verdict() + " in " + heap.wallMillis() + " ms, exit " + heap.exitCode());
        Judge.Result direct = judge(attack("", "ByteBuffer b = ByteBuffer.allocateDirect(1 << 30); say(\"allocated\");"), "strict", lim);
        record("C02 1 GiB direct buffer is refused", direct.verdict() != Judge.Verdict.ACCEPTED && !has(direct, "ATTACK allocated"), direct.verdict() + " " + direct.learnerOutput().replace('\n', ' ').trim());
        String unsafeBody = "Class<?> uc = Class.forName(\"sun.misc.Unsafe\"); Field f = uc.getDeclaredField(\"theUnsafe\"); f.setAccessible(true); Object u = f.get(null);"
                + " long chunk = 64L << 20; for (int i = 0; i < 24; i++) { long a = (Long) uc.getMethod(\"allocateMemory\", long.class).invoke(u, chunk);"
                + " uc.getMethod(\"setMemory\", long.class, long.class, byte.class).invoke(u, a, chunk, (byte) 1); say(\"native MiB=\" + ((i + 1) * 64)); }";
        Judge.Result nativeMem = judge(attack("", unsafeBody.replace("Field f", "java.lang.reflect.Field f")), "strict", lim);
        record("C03 native memory via Unsafe (bypasses -Xmx) is stopped by the cgroup limit (384M)", nativeMem.verdict() == Judge.Verdict.MEMORY_LIMIT || nativeMem.verdict() == Judge.Verdict.RUNTIME_ERROR,
                nativeMem.verdict() + " exit " + nativeMem.exitCode() + " last=" + lastLine(nativeMem.learnerOutput()));
        Judge.Result tmpfsFill = judge(attack("", "long total = 0; try (OutputStream o = new FileOutputStream(\"/tmp/big\")) { byte[] b = new byte[1 << 20]; for (int i = 0; i < 100; i++) { o.write(b); total += b.length; } } catch (Throwable t) { say(\"tmp write stopped after \" + (total >> 20) + \" MiB: \" + t.getMessage()); }"), "strict", lim);
        record("C04 filling /tmp stops at the tmpfs size (32 MiB), not the host disk", has(tmpfsFill, "tmp write stopped after") && !has(tmpfsFill, "after 100 MiB"), lastLine(tmpfsFill.learnerOutput()));

        // ============================================================ processes
        Judge.Result threads = judge(attack("", "int n = 0; List<Thread> ts = new ArrayList<>(); try { for (; n < 3000; n++) { Thread t = new Thread(() -> { try { Thread.sleep(60000); } catch (Exception e) { } }); t.setDaemon(true); t.start(); ts.add(t); } } catch (Throwable e) { say(\"thread bomb stopped: \" + e.getClass().getSimpleName()); } say(\"threads=\" + n);"), "strict", lim);
        int threadCount = intAfter(threads.learnerOutput(), "threads=");
        record("D01 thread bomb is capped by TasksMax=64 (3000 attempted)", (threadCount >= 0 && threadCount < 70) || threads.verdict() != Judge.Verdict.ACCEPTED, threads.verdict() + " threads=" + threadCount);
        Judge.Result procs = judge(attack("", "int ok = 0; for (int i = 0; i < 150; i++) { try { Runtime.getRuntime().exec(new String[]{\"/jdk/bin/java\", \"-Xmx8m\", \"-cp\", \"/judge/rt\", \"Launcher\"}); ok++; } catch (Throwable e) { say(\"exec stopped at \" + i + \": \" + e.getMessage()); break; } } say(\"spawned=\" + ok);"), "strict", lim);
        int spawned = intAfter(procs.learnerOutput(), "spawned=");
        record("D02 spawning JVM processes is capped by TasksMax (150 attempted)", (spawned >= 0 && spawned < 20) || procs.verdict() != Judge.Verdict.ACCEPTED, procs.verdict() + " spawned=" + spawned);
        Judge.Result shell = judge(attack("", "say(\"strict profile shell: \" + run(\"/bin/sh\", \"-c\", \"echo hi\"));"), "strict", lim);
        record("D03 strict profile has no shell to start", has(shell, "exec failed"), lastLine(shell.learnerOutput()));
        Judge.Result forkBomb = judge(attack("", "say(run(\"/bin/sh\", \"-c\", \"i=0; while [ $i -lt 300 ]; do sleep 120 >/dev/null 2>&1 & i=$((i+1)); done; echo pids_in_namespace=$(ls /proc | grep -c '^[0-9]'); echo spawned=$i\"));"), "probe", lim);
        int pidsSeen = intAfter(forkBomb.learnerOutput(), "pids_in_namespace=");
        record("D04 shell fork bomb (300 background sleeps) is capped at TasksMax=64 processes", (pidsSeen > 0 && pidsSeen <= 64) || has(forkBomb, "Cannot fork")
                // The same limit seen one step earlier: the sandbox was already at TasksMax, so the learner JVM could not even start the shell.
                // Seen intermittently (idle machine, then every run under heavy CPU load). Nothing ran; the cap held.
                || has(forkBomb, "unable to create native thread"), forkBomb.verdict() + " pids_in_namespace=" + pidsSeen + " :: " + forkBomb.learnerOutput().replace('\n', ';').trim());
        Judge.Result survivor = judge(attack("", "run(\"/bin/sh\", \"-c\", \"nohup sleep 121 >/dev/null 2>&1 &\"); say(\"started a background process\"); Thread.sleep(500);"), "probe", lim);
        Thread.sleep(500);
        String leftover = sh("bash", "-c", "pgrep -fc 'slee[p] 121' || true");
        record("D05 a background process does not outlive the job", leftover.equals("0"), "host processes matching 'sleep 121' after the job: " + leftover);

        // ============================================================ output
        Judge.Result floodOut = judge(attack("", "byte[] b = new byte[8192]; Arrays.fill(b, (byte) 'x'); while (true) System.out.write(b, 0, b.length);"), "strict", lim);
        record("E01 stdout flood: OUTPUT_LIMIT", floodOut.verdict() == Judge.Verdict.OUTPUT_LIMIT && floodOut.wallMillis() < 15_000, floodOut.verdict() + " in " + floodOut.wallMillis() + " ms");
        Judge.Result floodRaw = judge(attack("", "byte[] b = new byte[8192]; Arrays.fill(b, (byte) 'y'); OutputStream o = new FileOutputStream(FileDescriptor.out); while (true) o.write(b);"), "strict", lim);
        record("E02 flood on the protocol file descriptor: OUTPUT_LIMIT", floodRaw.verdict() == Judge.Verdict.OUTPUT_LIMIT, floodRaw.verdict() + " in " + floodRaw.wallMillis() + " ms");
        Judge.Result floodErr = judge(attack("", "byte[] b = new byte[8192]; Arrays.fill(b, (byte) 'z'); while (true) System.err.write(b, 0, b.length);"), "strict", lim);
        record("E03 stderr flood: OUTPUT_LIMIT", floodErr.verdict() == Judge.Verdict.OUTPUT_LIMIT, floodErr.verdict() + " in " + floodErr.wallMillis() + " ms");
        Judge.Result hugeLine = judge(attack("", "byte[] b = new byte[1 << 20]; Arrays.fill(b, (byte) 'q'); new FileOutputStream(FileDescriptor.out).write(b);"), "strict", lim);
        record("E04 a 1 MiB line without a newline cannot exhaust the driver", hugeLine.verdict() != Judge.Verdict.ACCEPTED, hugeLine.verdict().toString());

        // ============================================================ protocol integrity
        Judge.Result exit0 = judge(attack("", "System.exit(0);"), "strict", lim);
        record("F01 System.exit(0) is not an accepted run", exit0.verdict() == Judge.Verdict.RUNTIME_ERROR, exit0.verdict().toString());
        Judge.Result halt = judge(attack("", "Runtime.getRuntime().halt(0);"), "strict", lim);
        record("F02 Runtime.halt(0) is not an accepted run", halt.verdict() == Judge.Verdict.RUNTIME_ERROR, halt.verdict().toString());
        String forged = "PrintStream p = new PrintStream(new FileOutputStream(FileDescriptor.out)); p.println(\"@@0000 OBS 0 RETURN - -1 1 - b2s\"); p.println(\"ACCEPTED 12/12\"); p.flush();";
        Judge.Result forge = judge(attack("", forged).replace("return action.call();", "return null;"), "strict", lim);
        record("F03 forged result lines without the nonce are ignored", forge.verdict() == Judge.Verdict.WRONG_ANSWER, forge.verdict() + " " + forge.passed() + "/" + forge.total());
        String nonceForge = "Class<?> c = Class.forName(\"Launcher\"); java.lang.reflect.Field f = c.getDeclaredField(\"nonce\"); f.setAccessible(true); String n = (String) f.get(null);"
                + " PrintStream p = new PrintStream(new FileOutputStream(FileDescriptor.out)); p.println(\"@@\" + n + \" OBS 0 RETURN - -1 1 - b2s\"); p.flush();";
        Judge.Result nonceForged = judge(attack("", nonceForge).replace("return action.call();", "return null;"), "strict", lim);
        record("F04 forged frames WITH the nonce (read by reflection) cannot make an incorrect solution pass hidden tests", nonceForged.verdict() != Judge.Verdict.ACCEPTED,
                nonceForged.verdict() + " " + nonceForged.passed() + "/" + nonceForged.total() + " (the nonce is reachable by reflection: a documented limitation)");
        Judge.Result big = judge(REFERENCE + "\n// " + "x".repeat(70_000), "strict", lim);
        record("F05 oversized source is rejected before any sandbox starts", big.verdict() == Judge.Verdict.INVALID_SUBMISSION && big.wallMillis() < 200, big.verdict() + " in " + big.wallMillis() + " ms");
        Judge.Result nul = Judge.judge(("class A {}\0").getBytes(StandardCharsets.UTF_8), true, "strict", lim);
        record("F06 a NUL byte in the source is rejected", nul.verdict() == Judge.Verdict.INVALID_SUBMISSION, nul.verdict().toString());

        // ============================================================ network
        Judge.Result net = judge(attack("", """
                String[] hosts = "%s".split(",");
                for (String h : hosts) {
                    try (Socket s = new Socket()) { s.connect(new InetSocketAddress(h, %d), 1500); say("net " + h + " CONNECTED"); }
                    catch (Exception e) { say("net " + h + " blocked " + e.getClass().getSimpleName()); }
                }
                try (Socket s = new Socket()) { s.connect(new InetSocketAddress("8.8.8.8", 53), 1500); say("net 8.8.8.8 CONNECTED"); }
                catch (Exception e) { say("net 8.8.8.8 blocked " + e.getClass().getSimpleName()); }
                try { say("dns " + InetAddress.getByName("example.com")); } catch (Exception e) { say("dns blocked " + e.getClass().getSimpleName()); }
                try { StringBuilder sb = new StringBuilder(); for (NetworkInterface n : Collections.list(NetworkInterface.getNetworkInterfaces())) sb.append(n.getName()).append(n.isUp() ? "(up) " : "(down) "); say("interfaces " + sb); } catch (Exception e) { say("interfaces error " + e); }
                try (DatagramSocket d = new DatagramSocket()) { d.connect(new InetSocketAddress("8.8.8.8", 53)); d.send(new DatagramPacket(new byte[1], 1)); say("udp SENT"); } catch (Exception e) { say("udp blocked " + e.getClass().getSimpleName()); }
                """.formatted(String.join(",", hosts), listener.getLocalPort())), "strict", lim);
        Thread.sleep(300);
        record("G01 no network: every connect, DNS lookup and UDP send fails, the host listener saw 0 connections",
                !has(net, "CONNECTED") && !has(net, "udp SENT") && has(net, "dns blocked") && accepted.get() == 0,
                "hosts tried=" + hosts + "; accepted by host listener=" + accepted.get() + "; " + net.learnerOutput().replace("ATTACK ", "").replace('\n', ';').trim());

        // ============================================================ filesystem and environment
        String readBody = """
                String[] paths = {"/mnt", "/mnt/c", "/mnt/d", "/home", "/root", "/etc/passwd", "/etc/shadow", "/run", "/var", "/boot", "/sys/fs/cgroup", "%1$s", "%1$s/secret-sentinel.txt", "%1$s/jobs"};
                for (String p : paths) { Path pp = Path.of(p); say("path " + p + " exists=" + Files.exists(pp)); }
                say("root listing " + Arrays.toString(new File("/").list()));
                String tail = "%2$s"; long files = 0; boolean found = false; ArrayDeque<Path> q = new ArrayDeque<>(); q.add(Path.of("/"));
                while (!q.isEmpty() && files < 100000) {
                    Path d = q.poll(); String ds = d.toString();
                    if (ds.equals("/proc") || ds.equals("/dev") || ds.equals("/sys")) continue;
                    try (DirectoryStream<Path> st = Files.newDirectoryStream(d)) {
                        for (Path c : st) {
                            if (Files.isSymbolicLink(c)) continue;
                            if (Files.isDirectory(c)) q.add(c);
                            else if (Files.isRegularFile(c)) { files++; try { if (Files.size(c) < 262144 && new String(Files.readAllBytes(c), java.nio.charset.StandardCharsets.ISO_8859_1).contains("HIDDEN-SENTINEL-" + tail)) { found = true; say("SENTINEL FOUND IN " + c); } } catch (Exception e) { } }
                        }
                    } catch (Exception e) { }
                }
                say("walked regular files=" + files + " sentinelFound=" + found);
                try { String mi = new String(Files.readAllBytes(Path.of("/proc/self/mountinfo"))); say("mountinfo has /mnt=" + mi.contains("/mnt") + " has /home=" + mi.contains("/home")); } catch (Exception e) { say("mountinfo unreadable"); }
                """.formatted(BASE.toString(), SENTINEL_TAIL);
        // The probe walks the whole sandbox filesystem. Under CPU load the default 3 s per-test limit cut it off before it printed its verdict
        // (seen once in a full run, then reproduced with 12 busy loops): nothing had leaked, the check simply never finished. Only this probe gets a
        // longer per-test limit; every assertion below is unchanged, and it still requires the walk to FINISH with sentinelFound=false.
        Limits walkLimits = new Limits(lim.totalWallMillis(), lim.startupMillis(), 15_000, lim.outputCapBytes(), lim.sourceMaxBytes(), lim.memoryMax(), lim.tasksMax(),
                lim.cpuQuotaPercent(), lim.heapMb());
        Judge.Result fsRead = judge(attack("", readBody), "strict", walkLimits);
        boolean hostPathsVisible = fsRead.learnerOutput().contains("path /mnt exists=true") || fsRead.learnerOutput().contains("path /home exists=true")
                || fsRead.learnerOutput().contains("exists=true") && (fsRead.learnerOutput().contains("secret-sentinel.txt exists=true") || fsRead.learnerOutput().contains("jobs exists=true"));
        record("H01 host files (/mnt/c, /mnt/d, /home, host sentinel file, job dirs) are not visible; the sentinel is nowhere in the sandbox",
                !hostPathsVisible && has(fsRead, "sentinelFound=false") && !has(fsRead, "SENTINEL FOUND"),
                fsRead.learnerOutput().replace("ATTACK ", "").replace('\n', ';').trim());

        String writeBody = """
                String[] targets = {"/usr/x", "/jdk/x", "/judge/rt/x", "/src/x", "/x", "/etc/x", "/proc/sys/kernel/hostname", "/sys/x", "/dev/x", "/lib/x", "/tmp/allowed"};
                for (String t : targets) { try { Files.writeString(Path.of(t), "x"); say("write " + t + " SUCCEEDED"); } catch (Throwable e) { say("write " + t + " refused " + e.getClass().getSimpleName()); } }
                try { say("cgroup write: " + run("/bin/sh", "-c", "echo $$ > /sys/fs/cgroup/cgroup.procs 2>&1")); } catch (Throwable e) { }
                """;
        Judge.Result fsWrite = judge(attack("", writeBody), "probe", lim);
        boolean onlyTmp = count(fsWrite.learnerOutput(), "SUCCEEDED") == 1 && has(fsWrite, "write /tmp/allowed SUCCEEDED");
        record("H02 writes are refused everywhere except the size-limited /tmp", onlyTmp, fsWrite.learnerOutput().replace("ATTACK ", "").replace('\n', ';').trim());

        Judge.Result env = judge(attack("", """
                say("env keys " + new TreeSet<>(System.getenv().keySet()));
                say("env secret visible in process env=" + System.getenv().toString().contains("%1$s"));
                try { say("proc environ secret=" + new String(Files.readAllBytes(Path.of("/proc/self/environ"))).contains("%1$s")); } catch (Exception e) { say("environ unreadable"); }
                say("user.name=" + System.getProperty("user.name") + " user.dir=" + System.getProperty("user.dir"));
                """.formatted(ENV_SENTINEL)), "strict", lim);
        record("H03 no environment variable of the driver reaches the sandbox", has(env, "secret visible in process env=false") && !has(env, ENV_SENTINEL + "\n") && !has(env, "proc environ secret=true"),
                env.learnerOutput().replace("ATTACK ", "").replace('\n', ';').trim());

        // ============================================================ system calls, using real tools (probe profile)
        String py = """
                import ctypes, errno
                libc = ctypes.CDLL(None, use_errno=True)
                def t(name, nr, *args):
                    ctypes.set_errno(0)
                    r = libc.syscall(nr, *[ctypes.c_long(a) for a in args])
                    e = ctypes.get_errno()
                    print('SYS %s ret=%d errno=%s' % (name, r, errno.errorcode.get(e, e)))
                t('ptrace', 101, 0, 0, 0, 0)
                t('mount', 165, 0, 0, 0, 0, 0)
                t('unshare_newuser', 272, 0x10000000)
                t('unshare_newns', 272, 0x20000)
                t('unshare_newnet', 272, 0x40000000)
                t('setns', 308, -1, 0)
                t('chroot', 161, 0)
                t('socket_inet', 41, 2, 1, 0)
                t('socket_inet6', 41, 10, 1, 0)
                t('socket_netlink', 41, 16, 3, 0)
                t('socket_packet', 41, 17, 3, 0)
                t('bpf', 321, 0, 0, 0)
                t('keyctl', 250, 0, 0, 0, 0, 0)
                t('perf_event_open', 298, 0, 0, 0, 0, 0)
                t('reboot', 169, 0, 0, 0, 0)
                t('settimeofday', 164, 0, 0)
                t('socket_unix (allowed)', 41, 1, 1, 0)
                t('getpid (allowed)', 39)
                """;
        Judge.Result sys = judge(attack("", "say(\"\\n\" + run(\"/usr/bin/python3\", \"-c\", " + javaString(py) + "));"), "probe", lim);
        String so = sys.learnerOutput();
        boolean denied = so.contains("SYS ptrace ret=-1 errno=EPERM") && so.contains("SYS mount ret=-1 errno=EPERM") && so.contains("SYS unshare_newuser ret=-1 errno=EPERM")
                && so.contains("SYS unshare_newns ret=-1 errno=EPERM") && so.contains("SYS unshare_newnet ret=-1 errno=EPERM") && so.contains("SYS setns ret=-1 errno=EPERM")
                && so.contains("SYS chroot ret=-1 errno=EPERM") && so.contains("SYS socket_inet ret=-1 errno=EPERM") && so.contains("SYS socket_inet6 ret=-1 errno=EPERM")
                && so.contains("SYS socket_netlink ret=-1 errno=EPERM") && so.contains("SYS socket_packet ret=-1 errno=EPERM") && so.contains("SYS bpf ret=-1 errno=EPERM")
                && so.contains("SYS keyctl ret=-1 errno=EPERM") && so.contains("SYS perf_event_open ret=-1 errno=EPERM") && so.contains("SYS reboot ret=-1 errno=EPERM")
                && so.contains("SYS settimeofday ret=-1 errno=EPERM");
        boolean allowedOk = so.contains("SYS socket_unix (allowed) ret=") && !so.contains("SYS socket_unix (allowed) ret=-1") && so.contains("SYS getpid (allowed) ret=");
        record("I01 seccomp: ptrace, mount, namespaces, setns, chroot, INET/NETLINK/PACKET sockets, bpf, keyctl, perf, reboot, settimeofday all return EPERM; AF_UNIX and getpid still work",
                denied && allowedOk, so.replace("ATTACK ", "").replace("SYS ", "").replace('\n', ';').trim());

        // ============================================================ concurrency
        runConcurrency(lim);

        // ============================================================ host hygiene after everything
        Thread.sleep(1500);
        String units = sh("bash", "-c", "systemctl --user list-units --all --no-legend 'sl-judge-*' | wc -l");
        String sandboxJvms = sh("bash", "-c", "pgrep -fc '^/jdk/bin/java' || true");
        String sleepers = sh("bash", "-c", "pgrep -fc 'slee[p] 12[01]' || true");
        String jobs;
        try (var s = Files.list(BASE.resolve("jobs"))) {
            jobs = String.valueOf(s.count());
        }
        record("Z01 nothing is left behind: scopes=" + units + " sandbox JVMs=" + sandboxJvms + " leftover sleeps=" + sleepers + " job dirs=" + jobs,
                units.equals("0") && sandboxJvms.equals("0") && sleepers.equals("0") && jobs.equals("0"), "after " + report.size() + " cases");
        String memAfter = sh("free", "-m");
        System.out.println("---- memory before ----\n" + memBefore + "\n---- memory after ----\n" + memAfter);
        listener.close();
        System.out.println("\nRESULT: " + (report.size() - failures.get()) + " passed, " + failures.get() + " failed");
        System.exit(failures.get() == 0 ? 0 : 1);
    }

    private static void runConcurrency(Limits lim) throws Exception {
        String marker = """
                String me = "@@MARK@@"; Files.writeString(Path.of("/tmp/marker-" + me), me);
                Thread.sleep(700);
                int others = 0; for (String n : new File("/tmp").list()) if (n.startsWith("marker-") && !n.equals("marker-" + me)) others++;
                say("marker=" + me + " othersSeen=" + others + " pids=" + new File("/proc").list((d, n) -> n.matches("[0-9]+")).length);
                """;
        List<String> kinds = List.of("good", "loop", "heap", "good", "marker", "marker", "good", "loop");
        long t0 = System.nanoTime();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<Judge.Result>> futures = new ArrayList<>();
        for (String kind : kinds) {
            futures.add(pool.submit(() -> switch (kind) {
                case "good" -> judge(REFERENCE, "strict", lim);
                case "loop" -> judge(attack("", "while (true) { }"), "strict", lim);
                case "heap" -> judge(attack("", "List<byte[]> l = new ArrayList<>(); while (true) l.add(new byte[1 << 20]);"), "strict", lim);
                default -> judge(attack("", marker.replace("@@MARK@@", UUID.randomUUID().toString().substring(0, 8))), "strict", lim);
            }));
        }
        boolean ok = true;
        StringBuilder detail = new StringBuilder();
        boolean isolated = true;
        for (int i = 0; i < kinds.size(); i++) {
            Judge.Result r = futures.get(i).get();
            String kind = kinds.get(i);
            boolean expected = switch (kind) {
                case "good" -> r.verdict() == Judge.Verdict.ACCEPTED && r.passed() == 12;
                case "loop" -> r.verdict() == Judge.Verdict.TIME_LIMIT;
                case "heap" -> r.verdict() == Judge.Verdict.MEMORY_LIMIT;
                default -> r.verdict() == Judge.Verdict.ACCEPTED || r.verdict() == Judge.Verdict.WRONG_ANSWER;
            };
            if (kind.equals("marker")) {
                isolated &= has(r, "othersSeen=0");
            }
            ok &= expected;
            detail.append(kind).append('=').append(r.verdict()).append('(').append(r.wallMillis()).append("ms) ");
        }
        pool.shutdown();
        long total = (System.nanoTime() - t0) / 1_000_000L;
        record("J01 8 mixed jobs through 4 parallel workers: every verdict correct, no job affected another (" + total + " ms total)", ok, detail.toString());
        record("J02 parallel sandboxes cannot see each other's /tmp files (no marker of another job seen)", isolated, "markers checked on 2 parallel jobs");

        ExecutorService pool2 = Executors.newFixedThreadPool(4);
        List<Future<Judge.Result>> goods = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            goods.add(pool2.submit(() -> judge(REFERENCE, "strict", lim)));
        }
        boolean allGood = true;
        long worst = 0;
        for (Future<Judge.Result> f : goods) {
            Judge.Result r = f.get();
            allGood &= r.verdict() == Judge.Verdict.ACCEPTED && r.passed() == 12;
            worst = Math.max(worst, r.wallMillis());
        }
        pool2.shutdown();
        record("J03 four simultaneous correct submissions all ACCEPTED", allGood, "slowest " + worst + " ms");
    }

    private static String lastLine(String s) {
        String[] lines = s.trim().split("\n");
        return lines.length == 0 ? "" : lines[lines.length - 1];
    }

    private static int intAfter(String text, String key) {
        int i = text.lastIndexOf(key);
        if (i < 0) {
            return -1;
        }
        int j = i + key.length();
        int k = j;
        while (k < text.length() && Character.isDigit(text.charAt(k))) {
            k++;
        }
        return k > j ? Integer.parseInt(text.substring(j, k)) : -1;
    }

    private static String javaString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
    }
}
