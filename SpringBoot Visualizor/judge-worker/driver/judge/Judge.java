package judge;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The trusted judge for the pilot problem. It owns the hidden tests, starts one sandbox per job, and decides the verdict from
 * what the sandbox reports. Nothing the sandbox sends is believed without comparison with the expected observation computed here.
 */
public final class Judge {

    public enum Verdict { ACCEPTED, WRONG_ANSWER, COMPILE_ERROR, RUNTIME_ERROR, TIME_LIMIT, MEMORY_LIMIT, OUTPUT_LIMIT, INVALID_SUBMISSION, INTERNAL_ERROR }

    public record TestResult(String name, boolean visible, String status, String message) {
    }

    public record Result(Verdict verdict, List<TestResult> tests, int passed, int total, String compilerOutput, long wallMillis,
                         long peakKb, long cpuMillis, String learnerOutput, int exitCode) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The sandbox of the job this process is judging (a driver process judges one job), so that it can be killed from outside. */
    private static volatile Sandbox current;

    private static volatile Path currentJobDir;

    /** Kills the sandbox of the current job and removes its directory. Used when the caller goes away. */
    public static void cancelCurrent() {
        Sandbox sb = current;
        if (sb != null) {
            sb.kill();
        }
        Path dir = currentJobDir;
        if (dir != null) {
            deleteTree(dir);
        }
    }

    /**
     * Removes job directories that no running job can still own (older than five minutes; a job lasts at most about 20 seconds).
     * A driver that was killed from outside cannot clean up after itself, so the next driver does.
     */
    public static void sweepStaleJobs() {
        Path jobs = Sandbox.BASE.resolve("jobs");
        long cutoff = System.currentTimeMillis() - 5 * 60_000L;
        try (Stream<Path> children = Files.list(jobs)) {
            children.filter(p -> p.toFile().lastModified() < cutoff).forEach(Judge::deleteTree);
        } catch (IOException ignored) {
            // no jobs directory yet
        }
    }

    private Judge() {
    }

    /** @param submit false = Run (visible tests only); true = Submit (all tests). */
    public static Result judge(byte[] source, boolean submit, String profile, Limits limits) {
        return judge(source, submit, profile, limits, null);
    }

    /** @param jobId optional 16 hex characters chosen by the caller; the sandbox scope is then named sl-judge-JOBID so the caller can cancel it. */
    public static Result judge(byte[] source, boolean submit, String profile, Limits limits, String jobId) {
        return judge(source, submit, profile, limits, jobId, null);
    }

    /** @param custom the tests to run, supplied by the backend; null means the built-in tests (then {@code submit} selects visible only or all). */
    static Result judge(byte[] source, boolean submit, String profile, Limits limits, String jobId, List<? extends Scenarios.Case> custom) {
        return judge(source, submit, profile, limits, jobId, custom, Harness.RETRY_EXECUTOR);
    }

    /** @param harness which exercise this is; chosen by the caller (the backend), never by the learner's code. */
    static Result judge(byte[] source, boolean submit, String profile, Limits limits, String jobId, List<? extends Scenarios.Case> custom, Harness harness) {
        if (jobId != null && !jobId.matches("[0-9a-f]{16}")) {
            throw new IllegalArgumentException("invalid job id");
        }
        long started = System.nanoTime();
        if (custom == null && harness.scripted()) {
            throw new IllegalArgumentException("a scripted exercise has no built-in tests");
        }
        List<? extends Scenarios.Case> scenarios = custom != null ? custom : Scenarios.all().stream().filter(s -> submit || s.visible()).toList();
        List<TestResult> tests = new ArrayList<>();
        for (Scenarios.Case s : scenarios) {
            tests.add(new TestResult(s.name(), s.visible(), "NOT_RUN", null));
        }
        String invalid = validate(source, limits);
        if (invalid != null) {
            return done(Verdict.INVALID_SUBMISSION, tests, invalid, started, -1, -1, "", -1);
        }

        String jobKey = jobId != null ? jobId : UUID.randomUUID().toString().replace("-", "");
        Path jobDir = Sandbox.BASE.resolve("jobs").resolve(jobKey);
        Sandbox sandbox = null;
        try {
            Files.createDirectories(jobDir.resolve("src"), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            Files.write(jobDir.resolve("src/" + harness.className() + ".java"), source);
            currentJobDir = jobDir;
            String nonce = HexFormat.of().formatHex(randomBytes(16));
            sandbox = new Sandbox("sl-judge-" + jobKey.substring(0, 16), jobDir.resolve("src"), profile, limits, harness.name());
            current = sandbox;
            sandbox.write("NONCE " + nonce);
            return drive(sandbox, nonce, scenarios, tests, limits, started);
        } catch (Exception e) {
            return done(Verdict.INTERNAL_ERROR, tests, "judge failure: " + e.getClass().getSimpleName(), started, -1, -1, "", -1);
        } finally {
            if (sandbox != null) {
                sandbox.close();
            }
            deleteTree(jobDir);
        }
    }

    private static Result drive(Sandbox sb, String nonce, List<? extends Scenarios.Case> scenarios, List<TestResult> tests, Limits limits, long started)
            throws Exception {
        String prefix = "@@" + nonce + " ";
        Verdict verdict = null;
        String compilerOutput = "";
        long peak = -1;
        long cpu = -1;

        // phase 1: startup + compile
        String ready = awaitFrame(sb, prefix, limits.startupMillis());
        if (ready == null || ready.startsWith("\u0000")) {
            return finish(sb, tests, compilerOutput, started, peak, cpu, ready == null ? Verdict.TIME_LIMIT : classifyExit(sb));
        }
        if (ready.startsWith("CE ")) {
            compilerOutput = decode(ready.substring(3));
            return finish(sb, tests, compilerOutput, started, peak, cpu, Verdict.COMPILE_ERROR);
        }
        if (!ready.equals("READY")) {
            return finish(sb, tests, compilerOutput, started, peak, cpu, Verdict.INTERNAL_ERROR);
        }

        // phase 2: one scenario at a time, so a hang is attributed to one test
        boolean allPassed = true;
        for (int i = 0; i < scenarios.size(); i++) {
            Scenarios.Case sc = scenarios.get(i);
            sb.write(sc.wire(i));
            long budget = Math.min(limits.perTestMillis(), Math.max(1, limits.totalWallMillis() - elapsedMillis(started)));
            String frame = awaitFrame(sb, prefix, budget);
            if (frame == null || frame.startsWith("\u0000")) {
                verdict = frame == null ? Verdict.TIME_LIMIT : classifyExit(sb);
                tests.set(i, new TestResult(sc.name(), sc.visible(), verdict == Verdict.TIME_LIMIT ? "TIMEOUT" : "ERROR",
                        sc.visible() ? "the program stopped responding or ended during this test" : null));
                allPassed = false;
                break;
            }
            String problem;
            try {
                problem = sc.check(frame, i);
            } catch (IllegalArgumentException e) {
                tests.set(i, new TestResult(sc.name(), sc.visible(), "ERROR", sc.visible() ? "no valid result was reported for this test" : null));
                allPassed = false;
                verdict = Verdict.RUNTIME_ERROR;
                break;
            }
            tests.set(i, new TestResult(sc.name(), sc.visible(), problem == null ? "PASS" : "FAIL", problem == null || !sc.visible() ? null : problem));
            allPassed &= problem == null;
        }

        if (verdict == null) {
            sb.write("END");
            String stat = awaitFrame(sb, prefix, 2_000);
            if (stat != null && stat.startsWith("STAT ")) {
                String[] f = stat.split(" ");
                peak = Long.parseLong(f[1]);
                cpu = Long.parseLong(f[2]);
            }
            verdict = allPassed ? Verdict.ACCEPTED : Verdict.WRONG_ANSWER;
        }
        return finish(sb, tests, compilerOutput, started, peak, cpu, verdict);
    }

    /** Waits for a valid frame, ignoring anything else the program prints. Returns null on timeout, "\0..." when the output ended. */
    private static String awaitFrame(Sandbox sb, String prefix, long millis) throws InterruptedException {
        long deadline = System.nanoTime() + millis * 1_000_000L;
        while (true) {
            if (sb.outputExceeded()) {
                return "\u0000OUTPUT";
            }
            long left = (deadline - System.nanoTime()) / 1_000_000L;
            if (left <= 0) {
                return null;
            }
            String line = sb.poll(Math.min(left, 50));
            if (line == null) {
                continue;
            }
            if (line.equals(Sandbox.EOF)) {
                return "\u0000EOF";
            }
            if (line.startsWith(prefix)) {
                return line.substring(prefix.length());
            }
            // anything without the nonce is learner output: ignored
        }
    }

    private static Verdict classifyExit(Sandbox sb) throws InterruptedException {
        if (sb.outputExceeded()) {
            return Verdict.OUTPUT_LIMIT;
        }
        for (int i = 0; i < 40 && !sb.exited(); i++) {
            Thread.sleep(50);
        }
        String err = sb.errorOutput();
        int code = sb.exitCode();
        // 3 = the JVM's -XX:+ExitOnOutOfMemoryError exit status; 137/143 = killed (the cgroup memory limit kills with SIGKILL)
        if (err.contains("OutOfMemoryError") || code == 3 || code == 137 || code == 9 || code == 143) {
            return Verdict.MEMORY_LIMIT;
        }
        return Verdict.RUNTIME_ERROR;
    }

    private static Result finish(Sandbox sb, List<TestResult> tests, String compilerOutput, long started, long peak, long cpu, Verdict verdict)
            throws InterruptedException {
        if (sb.outputExceeded() && verdict != Verdict.COMPILE_ERROR) {
            verdict = Verdict.OUTPUT_LIMIT;
        }
        sb.close();
        if (!sb.exited()) {
            Thread.sleep(100);
        }
        return done(verdict, tests, compilerOutput, started, peak, cpu, sb.errorOutput(), sb.exitCode());
    }

    private static Result done(Verdict verdict, List<TestResult> tests, String compilerOutput, long started, long peak, long cpu, String learnerOutput, int exit) {
        int passed = (int) tests.stream().filter(t -> t.status().equals("PASS")).count();
        return new Result(verdict, tests, passed, tests.size(), compilerOutput, elapsedMillis(started), peak, cpu, learnerOutput, exit);
    }

    private static String validate(byte[] source, Limits limits) {
        if (source == null || source.length == 0) {
            return "the source is empty";
        }
        if (source.length > limits.sourceMaxBytes()) {
            return "the source is larger than " + limits.sourceMaxBytes() + " bytes";
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(source)).toString();
            if (text.indexOf('\0') >= 0) {
                return "the source contains a NUL character";
            }
        } catch (CharacterCodingException e) {
            return "the source is not valid UTF-8";
        }
        return null;
    }

    private static String decode(String b64) {
        return b64.equals("-") ? "" : new String(Base64.getUrlDecoder().decode(b64), StandardCharsets.UTF_8);
    }

    private static byte[] randomBytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    private static long elapsedMillis(long started) {
        return (System.nanoTime() - started) / 1_000_000L;
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException ignored) {
            // already gone
        }
    }
}
