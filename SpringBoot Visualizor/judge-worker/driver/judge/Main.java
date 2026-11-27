package judge;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Command line entry: {@code judge run|submit [--profile strict|probe] [--debug] [--job HEX16] [--framed] <source file | ->}, {@code judge check} and {@code judge cancel HEX16}. Prints one JSON object.
 * A source of "-" is read from stdin, which is how a caller outside WSL passes code in without sharing any directory.
 * Learner output is included only with --debug (the adversarial suite uses it); a real caller would not show it for hidden tests.
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        boolean submit = false;
        boolean debug = false;
        boolean framed = false;
        String command = "run";
        String profile = "strict";
        String jobId = null;
        String source = null;
        Harness harness = Harness.RETRY_EXECUTOR;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "run", "submit", "check", "cancel" -> {
                    command = args[i];
                    submit = args[i].equals("submit");
                }
                case "--debug" -> debug = true;
                case "--framed" -> framed = true;
                case "--profile" -> profile = args[++i];
                case "--job" -> jobId = args[++i];
                case "--harness" -> harness = Harness.valueOf(args[++i]);
                default -> source = args[i];
            }
        }
        if (command.equals("check")) {
            System.out.println(check());
            return;
        }
        if (command.equals("cancel")) {
            System.out.println(cancel(source));
            return;
        }
        if (source == null) {
            System.err.println("usage: judge run|submit [--profile strict|probe] [--debug] [--job <16 hex>] [--framed] <source file | ->\n"
                    + "       judge check | judge cancel <16 hex>");
            System.exit(2);
        }
        Judge.sweepStaleJobs();
        byte[] bytes;
        java.util.List<Scenarios.Case> tests = null;
        if (framed) {
            // framed: "LEN <n>\n" then exactly n bytes. stdin then stays open, and its closing means "the caller is gone": kill the sandbox.
            bytes = readFramed(System.in, Limits.defaults().sourceMaxBytes());
            tests = readTests(System.in, harness);
            // SIGHUP / SIGTERM (for example when the caller's wsl.exe disappears) must not leave a sandbox or a job directory behind
            Runtime.getRuntime().addShutdownHook(new Thread(Judge::cancelCurrent, "cancel-on-exit"));
            Thread watcher = new Thread(() -> {
                try {
                    while (System.in.read() >= 0) {
                        // nothing else is expected
                    }
                } catch (java.io.IOException ignored) {
                    // closed
                }
                Judge.cancelCurrent();
                Runtime.getRuntime().halt(4);
            }, "caller-watch");
            watcher.setDaemon(true);
            watcher.start();
        } else {
            bytes = source.equals("-") ? readLimited(System.in, 1 << 20) : Files.readAllBytes(Path.of(source));
        }
        System.out.println(json(Judge.judge(bytes, submit, profile, Limits.defaults(), jobId, tests, harness), debug));
        System.out.flush();
        Runtime.getRuntime().halt(0); // do not wait for the caller-watch thread
    }

    private static byte[] readFramed(InputStream in, int max) throws Exception {
        StringBuilder header = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n' && header.length() < 20) {
            header.append((char) c);
        }
        if (!header.toString().matches("LEN [0-9]{1,9}")) {
            throw new IllegalArgumentException("bad frame header");
        }
        int n = Integer.parseInt(header.substring(4));
        if (n > max) {
            // the judge rejects an oversized source itself: hand it something that is too large without reading it
            return new byte[max + 1];
        }
        byte[] bytes = in.readNBytes(n);
        if (bytes.length != n) {
            throw new IllegalArgumentException("short frame");
        }
        return bytes;
    }

    /** After the source: {@code TESTS k} and k test lines. k = 0 means "use the built-in tests". */
    private static java.util.List<Scenarios.Case> readTests(InputStream in, Harness harness) throws Exception {
        String header = readLine(in, 20);
        if (header == null || !header.matches("TESTS [0-9]{1,2}")) {
            throw new IllegalArgumentException("bad tests header");
        }
        int k = Integer.parseInt(header.substring(6));
        if (k > 50) {
            throw new IllegalArgumentException("too many tests");
        }
        if (k == 0) {
            if (harness.scripted()) {
                throw new IllegalArgumentException("a scripted exercise needs its tests");
            }
            return null;
        }
        java.util.List<Scenarios.Case> tests = new java.util.ArrayList<>();
        for (int i = 0; i < k; i++) {
            String line = readLine(in, 4096);
            if (line == null) {
                throw new IllegalArgumentException("short tests");
            }
            tests.add(harness.scripted() ? Scenarios.fromScriptWire(line, harness) : Scenarios.fromWire(line));
        }
        return tests;
    }

    private static String readLine(InputStream in, int max) throws Exception {
        StringBuilder b = new StringBuilder();
        int c;
        while ((c = in.read()) >= 0 && c != '\n') {
            if (b.length() >= max) {
                throw new IllegalArgumentException("line too long");
            }
            b.append((char) c);
        }
        return c < 0 && b.length() == 0 ? null : b.toString();
    }

    /** Verifies that this machine can run the sandbox at all. Never starts a learner program. */
    static String check() {
        try {
            Path base = Sandbox.BASE;
            String[] needed = {"jdk17/bin/java", "run-in-sandbox.sh", "seccomp.bpf", "build/rt/Launcher.class", "build/rt/Sleeper.class", "build/rt/TransientException.class"};
            for (String f : needed) {
                if (!Files.exists(base.resolve(f))) {
                    return "{\"ok\":false,\"reason\":\"worker file missing: " + f + "\"}";
                }
            }
            if (run("bwrap", "--version") != 0) {
                return "{\"ok\":false,\"reason\":\"bubblewrap is not available\"}";
            }
            if (run("systemd-run", "--user", "--scope", "--quiet", "true") != 0) {
                return "{\"ok\":false,\"reason\":\"systemd user scopes are not available\"}";
            }
            return "{\"ok\":true}";
        } catch (Exception e) {
            return "{\"ok\":false,\"reason\":\"check failed\"}";
        }
    }

    /** Kills the sandbox scope of a job that was started with --job. */
    static String cancel(String jobId) throws Exception {
        if (jobId == null || !jobId.matches("[0-9a-f]{16}")) {
            return "{\"cancelled\":false,\"reason\":\"invalid job id\"}";
        }
        String unit = "sl-judge-" + jobId + ".scope";
        run("systemctl", "--user", "kill", "--signal=SIGKILL", "--kill-whom=all", unit);
        run("systemctl", "--user", "stop", unit);
        return "{\"cancelled\":true}";
    }

    private static int run(String... cmd) throws Exception {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        p.getInputStream().readAllBytes();
        if (!p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS)) {
            p.destroyForcibly();
            return -1;
        }
        return p.exitValue();
    }

    private static byte[] readLimited(InputStream in, int max) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0 && out.size() <= max) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    static String json(Judge.Result r, boolean debug) {
        StringBuilder s = new StringBuilder("{");
        s.append("\"verdict\":\"").append(r.verdict()).append("\",\"passed\":").append(r.passed()).append(",\"total\":").append(r.total());
        s.append(",\"wallMillis\":").append(r.wallMillis()).append(",\"peakKb\":").append(r.peakKb()).append(",\"cpuMillis\":").append(r.cpuMillis());
        s.append(",\"compilerOutput\":").append(str(r.compilerOutput())).append(",\"tests\":[");
        for (int i = 0; i < r.tests().size(); i++) {
            Judge.TestResult t = r.tests().get(i);
            s.append(i == 0 ? "" : ",").append("{\"name\":").append(str(t.name())).append(",\"visible\":").append(t.visible())
                    .append(",\"status\":\"").append(t.status()).append("\",\"message\":").append(t.message() == null ? "null" : str(t.message())).append('}');
        }
        s.append(']');
        if (debug) {
            s.append(",\"learnerOutput\":").append(str(r.learnerOutput())).append(",\"exitCode\":").append(r.exitCode());
        }
        return s.append('}').toString();
    }

    private static String str(String v) {
        StringBuilder s = new StringBuilder("\"");
        for (char c : v.toCharArray()) {
            switch (c) {
                case '"' -> s.append("\\\"");
                case '\\' -> s.append("\\\\");
                case '\n' -> s.append("\\n");
                case '\r' -> s.append("\\r");
                case '\t' -> s.append("\\t");
                default -> s.append(c < 0x20 ? String.format("\\u%04x", (int) c) : String.valueOf(c));
            }
        }
        return s.append('"').toString();
    }

    private Main() {
    }
}
