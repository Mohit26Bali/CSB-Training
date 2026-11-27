package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The backend against the REAL judge-worker in Ubuntu WSL: real compilation, real sandboxed execution, real limits, real kills.
 * The tests are skipped (visibly, with the reason) on a machine where the WSL worker is not installed; on this project's development
 * machine they run. Nothing here is mocked. The worker must have been built with {@code judge-worker/build.sh}.
 *
 * <p>Override the location with -Dspringlab.coding.test.wsl-distro=... and -Dspringlab.coding.test.wsl-base-dir=...
 */
class WslExecutionIntegrationTest {

    private static String distro;
    private static String baseDir;
    private static ExecutionProperties.Wsl config;
    private static boolean workerAvailable;
    private static String skipReason = "";

    private final List<ExecutionService> services = new ArrayList<>();

    @BeforeAll
    static void locateWorker() throws Exception {
        distro = System.getProperty("springlab.coding.test.wsl-distro", "Ubuntu");
        baseDir = System.getProperty("springlab.coding.test.wsl-base-dir");
        if (baseDir == null) {
            String home = wsl(distro, "printenv", "HOME").trim();
            baseDir = home.startsWith("/") && !home.contains("\n") ? home + "/springlab-judge" : "";
        }
        config = new ExecutionProperties.Wsl("wsl.exe", distro, baseDir);
        Availability a = baseDir.isEmpty() ? Availability.down("wsl", "no WSL home directory") : new WslBackend(config, new ObjectMapper()).availability();
        workerAvailable = a.available();
        skipReason = a.reason();
    }

    private static void requireWorker() {
        assumeTrue(workerAvailable, "WSL worker not available: " + skipReason);
    }

    @AfterEach
    void stop() {
        services.forEach(ExecutionService::shutdown);
    }

    private ExecutionService service(int workers, int queue) {
        ExecutionService s = new ExecutionService(new WslBackend(config, new ObjectMapper()),
                new ExecutionProperties(ExecutionProperties.Backend.WSL, workers, queue, 60, config));
        services.add(s);
        return s;
    }

    private static ExecutionRequest request(String source, ExecutionRequest.Mode mode) {
        return new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, mode, source.getBytes(StandardCharsets.UTF_8));
    }

    private static ExecutionStatus awaitDone(ExecutionService s, String user, String id, Duration limit) throws InterruptedException {
        long end = System.nanoTime() + limit.toNanos();
        while (System.nanoTime() < end) {
            ExecutionStatus st = s.status(user, id).orElseThrow();
            if (st.state() == ExecutionState.FINISHED || st.state() == ExecutionState.CANCELLED) {
                return st;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("job did not finish in " + limit);
    }

    private static ExecutionResult judge(ExecutionService s, String user, String source, ExecutionRequest.Mode mode) throws InterruptedException {
        String id = s.submit(user, request(source, mode));
        return awaitDone(s, user, id, Duration.ofSeconds(90)).result();
    }

    private static String wsl(String distro, String... command) throws Exception {
        List<String> c = new ArrayList<>(List.of("wsl.exe", "-d", distro, "--exec"));
        c.addAll(List.of(command));
        Process p = new ProcessBuilder(c).redirectErrorStream(true).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        p.waitFor(60, TimeUnit.SECONDS);
        return out;
    }

    private static int sandboxScopes() throws Exception {
        String out = wsl(distro, "systemctl", "--user", "list-units", "--all", "--no-legend", "sl-judge-*");
        return (int) out.lines().filter(l -> l.contains("sl-judge-")).count();
    }

    private static int sandboxJvms() throws Exception {
        String out = wsl(distro, "pgrep", "-fc", "^/jdk/bin/java");
        return Integer.parseInt(out.trim().isEmpty() ? "0" : out.trim());
    }

    private static void await(BooleanSupplier condition, Duration limit) throws InterruptedException {
        long end = System.nanoTime() + limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("condition not reached in " + limit);
            }
            Thread.sleep(200);
        }
    }

    // ------------------------------------------------------------------------------------------------ real verdicts

    @Test
    void aCorrectSolutionIsAcceptedByTheRealSandbox() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.REFERENCE, ExecutionRequest.Mode.SUBMIT);
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.passed()).isEqualTo(12);
        assertThat(r.total()).isEqualTo(12);
        assertThat(r.tests().stream().filter(t -> !t.visible())).hasSize(9).allMatch(t -> t.message() == null);
        assertThat(r.compilerOutput()).isEmpty();
        assertThat(r.peakKb()).isPositive();
    }

    @Test
    void runJudgesOnlyTheVisibleTests() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.REFERENCE, ExecutionRequest.Mode.RUN);
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.tests()).allMatch(ExecutionResult.TestOutcome::visible);
    }

    @Test
    void aWrongSolutionIsWrongAnswerWithoutHiddenDetail() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.WRONG, ExecutionRequest.Mode.SUBMIT);
        assertThat(r.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(r.passed()).isLessThan(r.total());
        assertThat(r.tests().stream().filter(t -> !t.visible())).allMatch(t -> t.message() == null);
    }

    @Test
    void aFailedVisibleTestExplainsItselfButTheHiddenOnesDoNot() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.NEVER_RETRIES, ExecutionRequest.Mode.SUBMIT);
        assertThat(r.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(r.tests().stream().filter(t -> t.visible() && t.status().equals("FAIL"))).isNotEmpty().allMatch(t -> t.message() != null && !t.message().isBlank());
        assertThat(r.tests().stream().filter(t -> !t.visible())).allMatch(t -> t.message() == null);
    }

    @Test
    void aCompileErrorIsReportedWithoutHostPaths() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.COMPILE_ERROR, ExecutionRequest.Mode.SUBMIT);
        assertThat(r.verdict()).isEqualTo(Verdict.COMPILE_ERROR);
        assertThat(r.compilerOutput()).contains("RetryExecutor.java:").doesNotContain("/src/").doesNotContain("/tmp/").doesNotContain("/home/");
    }

    @Test
    void anInfiniteLoopIsTimeLimit() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.INFINITE_LOOP, ExecutionRequest.Mode.SUBMIT);
        assertThat(r.verdict()).isEqualTo(Verdict.TIME_LIMIT);
        await(() -> {
            try {
                return sandboxScopes() == 0;
            } catch (Exception e) {
                return false;
            }
        }, Duration.ofSeconds(15));
    }

    @Test
    void aHeapBombIsMemoryLimit() throws Exception {
        requireWorker();
        assertThat(judge(service(2, 2), "ann", LearnerSources.HEAP_BOMB, ExecutionRequest.Mode.SUBMIT).verdict()).isEqualTo(Verdict.MEMORY_LIMIT);
    }

    @Test
    void anOutputFloodIsOutputLimit() throws Exception {
        requireWorker();
        assertThat(judge(service(2, 2), "ann", LearnerSources.OUTPUT_FLOOD, ExecutionRequest.Mode.SUBMIT).verdict()).isEqualTo(Verdict.OUTPUT_LIMIT);
    }

    @Test
    void systemExitIsARuntimeErrorNeverAnAcceptance() throws Exception {
        requireWorker();
        assertThat(judge(service(2, 2), "ann", LearnerSources.EXIT, ExecutionRequest.Mode.SUBMIT).verdict()).isEqualTo(Verdict.RUNTIME_ERROR);
    }

    @Test
    void theResultHasNowhereToCarryLearnerOutputOrHiddenData() throws Exception {
        requireWorker();
        ExecutionResult r = judge(service(2, 2), "ann", LearnerSources.CHATTY, ExecutionRequest.Mode.SUBMIT);
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.toString()).doesNotContain("LEARNER-PRINTED-MARKER").doesNotContain("LEARNER-STDOUT-MARKER");
    }

    // ------------------------------------------------------------------------------------------------ tests supplied by the backend

    @Test
    void theWorkerJudgesExactlyTheTestsItIsGivenAndComparesWithTheExpectationsItIsGiven() throws Exception {
        requireWorker();
        TestSpec right = new TestSpec("first call succeeds", true, 3, 100, 800, List.of("R:ok"), "RETURN", "", -1, 1, List.of(), "ok");
        TestSpec secret = new TestSpec("two retries", false, 4, 100, 800, List.of("T:a", "T:b", "R:done"), "RETURN", "", -1, 3, List.of(100L, 200L), "done");
        TestSpec wrongExpectation = new TestSpec("expectation is deliberately wrong", false, 4, 100, 800, List.of("T:a", "T:b", "R:done"), "RETURN", "", -1, 3, List.of(100L, 999L), "done");
        ExecutionService s = service(2, 2);
        String id = s.submit("ann", new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT,
                LearnerSources.REFERENCE.getBytes(StandardCharsets.UTF_8), List.of(right, secret, wrongExpectation)));
        ExecutionResult r = awaitDone(s, "ann", id, Duration.ofSeconds(90)).result();
        assertThat(r.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(r.total()).isEqualTo(3);
        assertThat(r.tests()).extracting(ExecutionResult.TestOutcome::name).containsExactly("first call succeeds", "two retries", "expectation is deliberately wrong");
        assertThat(r.tests()).extracting(ExecutionResult.TestOutcome::status).containsExactly("PASS", "PASS", "FAIL");
        assertThat(r.tests().get(2).message()).as("a hidden test never carries a message out").isNull();
        assertThat(r.tests()).extracting(ExecutionResult.TestOutcome::visible).containsExactly(true, false, false);
    }

    @Test
    void aMalformedTestsBlockIsRejectedByTheWorkerAndNeverProducesAnAcceptance() throws Exception {
        requireWorker();
        WslBackend backend = new WslBackend(config, new ObjectMapper());
        char nl = (char) 10;
        String[] bad = {
                "TESTS 1" + nl + "this is not a test line" + nl,
                "TESTS 1" + nl + "TEST 0 1 bmFtZQ 3 100 800 R:ok RETURN - -1 1 -" + nl,                    // 13 fields instead of 14
                "TESTS 2" + nl + "TEST 0 1 bmFtZQ 3 100 800 R:ok RETURN - -1 1 - b2s" + nl,               // one valid line where two were announced, then EOF
                "TESTS 99" + nl,
                "TESTS x" + nl,
                "TESTS 1" + nl + "TEST 0 1 bmFtZQ 3 100 800 Q:ok RETURN - -1 1 - b2s" + nl,                // unknown step kind
                "TESTS 1" + nl + "TEST 0 1 bmFtZQ notanumber 100 800 R:ok RETURN - -1 1 - b2s" + nl,
                "",                                                                                  // no tests block at all
        };
        for (String block : bad) {
            String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Process p = WorkerLauncher.processBuilder().start(backend.command("submit", "--framed", "--job", jobId, "-"));
            byte[] source = LearnerSources.REFERENCE.getBytes(StandardCharsets.UTF_8);
            p.getOutputStream().write(("LEN " + source.length + nl).getBytes(StandardCharsets.US_ASCII));
            p.getOutputStream().write(source);
            p.getOutputStream().write(block.getBytes(StandardCharsets.US_ASCII));
            p.getOutputStream().flush();
            p.getOutputStream().close(); // EOF: the worker must not be left waiting
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(p.waitFor(60, TimeUnit.SECONDS)).as(block).isTrue();
            assertThat(out).as(block).doesNotContain("ACCEPTED");
            assertThat(p.exitValue()).as(block).isNotZero();
        }
        assertThat(sandboxScopes()).isZero();
    }

    private static String b64(String s) {
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void aMalformedScriptedTestsBlockIsRejectedByTheWorkerAndNeverProducesAnAcceptance() throws Exception {
        requireWorker();
        WslBackend backend = new WslBackend(config, new ObjectMapper());
        char nl = (char) 10;
        String name = b64("t");
        String ok = "SCRIPT 0 1 " + name + " " + b64("new 2 100" + nl + "state") + " " + b64("-" + nl + "CLOSED");
        // {harness, tests block}: every one must be refused by the worker's own validation, whatever the backend sent
        String[][] bad = {
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("rm -rf /") + " " + b64("-") + nl},                              // operation not in the list
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("relay 3") + " " + b64("0") + nl},                            // another exercise's operation
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("new 2 100;ls") + " " + b64("-") + nl},                         // characters outside the alphabet
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("new 2 100" + nl + "state") + " " + b64("-") + nl},           // trace shorter than the script
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("state") + " " + b64("CLOSED" + nl + "OPEN") + nl},             // trace longer than the script
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("state") + " " + b64("CLO$ED") + nl},                          // bad expected line
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + "!!notbase64!!" + " " + b64("-") + nl},                            // not base64
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("state") + nl},                                                // a field missing
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 2 " + name + " " + b64("state") + " " + b64("CLOSED") + nl},                          // visibility is not 0 or 1
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("state ".repeat(201).trim().replace(" ", nl + "")) + " " + b64("x") + nl}, // far too many operations
                {"CIRCUIT_BREAKER", "TESTS 1" + nl + "TEST 0 1 bmFtZQ 3 100 800 R:ok RETURN - -1 1 - b2s" + nl},                                      // a retry-style test for a scripted exercise
                {"RETRY_EXECUTOR", "TESTS 1" + nl + ok + nl},                                                                                        // a scripted test for the retry exercise
                {"CIRCUIT_BREAKER", "TESTS 0" + nl},                                                                                                 // a scripted exercise with no tests
                {"CIRCUIT_BREAKER", "TESTS 1" + nl},                                                                                                 // announced but missing
                {"NOT_A_HARNESS", "TESTS 1" + nl + ok + nl},                                                                                         // unknown harness
                {"circuit_breaker", "TESTS 1" + nl + ok + nl},                                                                                       // harness names are exact
                {"PASSWORD_VALIDATOR", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("exec validate") + " " + b64("-") + nl},                      // an operation outside new/call
                {"PASSWORD_VALIDATOR", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("call validate a;ls") + " " + b64("-") + nl},               // characters outside the alphabet
                {"PASSWORD_VALIDATOR", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("new" + nl + "call firstError x") + " " + b64("-") + nl},    // trace shorter than the script
                {"BEAN_REGISTRY", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("relay 3") + " " + b64("0") + nl},                              // another exercise's operation
                {"BEAN_REGISTRY", "TESTS 1" + nl + "SCRIPT 0 1 " + name + " " + b64("new") + " " + b64("-") + nl + "SCRIPT 0 1 " + name + " " + b64("new") + " " + b64("-") + nl}, // more tests than announced
        };
        for (String[] c : bad) {
            String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            Process p = WorkerLauncher.processBuilder().start(backend.command("submit", "--framed", "--harness", c[0], "--job", jobId, "-"));
            byte[] source = "public final class CircuitBreaker { }".getBytes(StandardCharsets.UTF_8);
            p.getOutputStream().write(("LEN " + source.length + nl).getBytes(StandardCharsets.US_ASCII));
            p.getOutputStream().write(source);
            p.getOutputStream().write(c[1].getBytes(StandardCharsets.US_ASCII));
            p.getOutputStream().flush();
            p.getOutputStream().close();
            String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(p.waitFor(60, TimeUnit.SECONDS)).as(c[0] + " " + c[1]).isTrue();
            assertThat(out).as(c[0] + " " + c[1]).doesNotContain("ACCEPTED");
            assertThat(p.exitValue()).as(c[0] + " " + c[1]).isNotZero();
        }
        assertThat(sandboxScopes()).isZero();
    }

    // ------------------------------------------------------------------------------------------------ per-user, queue, concurrency

    @Test
    void oneInFlightJobPerUserAndCancelKillsTheWholeSandbox() throws Exception {
        requireWorker();
        ExecutionService s = service(2, 2);
        String id = s.submit("ann", request(LearnerSources.INFINITE_LOOP, ExecutionRequest.Mode.SUBMIT));
        assertThatThrownBy(() -> s.submit("ann", request(LearnerSources.REFERENCE, ExecutionRequest.Mode.SUBMIT))).isInstanceOf(ExecutionBusyException.class);
        await(() -> s.runningCount() == 1, Duration.ofSeconds(30));
        Thread.sleep(2500); // let the sandbox start and the loop run
        long cancelStarted = System.nanoTime();
        s.cancel("ann", id);
        ExecutionStatus done = awaitDone(s, "ann", id, Duration.ofSeconds(30));
        assertThat(done.state()).isEqualTo(ExecutionState.CANCELLED);
        assertThat(done.result().verdict()).isEqualTo(Verdict.CANCELLED);
        assertThat(Duration.ofNanos(System.nanoTime() - cancelStarted).toSeconds()).isLessThan(20);
        await(() -> {
            try {
                return sandboxScopes() == 0 && sandboxJvms() == 0;
            } catch (Exception e) {
                return false;
            }
        }, Duration.ofSeconds(15));
        assertThat(s.submit("ann", request(LearnerSources.REFERENCE, ExecutionRequest.Mode.SUBMIT))).isNotNull();
    }

    @Test
    void theRealQueueRefusesWith429WhenWorkersAndQueueAreFull() throws Exception {
        requireWorker();
        ExecutionService s = service(2, 1);
        List<String> users = List.of("u1", "u2", "u3");
        List<String> ids = new ArrayList<>();
        for (String u : users) {
            ids.add(s.submit(u, request(LearnerSources.INFINITE_LOOP, ExecutionRequest.Mode.SUBMIT)));
        }
        assertThatThrownBy(() -> s.submit("u4", request(LearnerSources.REFERENCE, ExecutionRequest.Mode.SUBMIT))).isInstanceOf(ExecutionCapacityException.class);
        for (int i = 0; i < users.size(); i++) {
            s.cancel(users.get(i), ids.get(i));
        }
        for (int i = 0; i < users.size(); i++) {
            assertThat(awaitDone(s, users.get(i), ids.get(i), Duration.ofSeconds(60)).state()).isEqualTo(ExecutionState.CANCELLED);
        }
        await(() -> s.inFlightCount() == 0, Duration.ofSeconds(10));
        await(() -> {
            try {
                return sandboxScopes() == 0 && sandboxJvms() == 0;
            } catch (Exception e) {
                return false;
            }
        }, Duration.ofSeconds(20));
    }

    @Test
    void eightUsersThroughFourWorkersAreAllJudgedCorrectlyAndNeverMoreThanFourRunAtOnce() throws Exception {
        requireWorker();
        ExecutionService s = service(4, 4);
        List<String> users = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            users.add("user" + i);
            String source = i % 4 == 3 ? LearnerSources.WRONG : LearnerSources.REFERENCE;
            ids.add(s.submit(users.get(i), request(source, ExecutionRequest.Mode.SUBMIT)));
        }
        for (int i = 0; i < 8; i++) {
            ExecutionResult r = awaitDone(s, users.get(i), ids.get(i), Duration.ofSeconds(120)).result();
            assertThat(r.verdict()).as("user" + i).isEqualTo(i % 4 == 3 ? Verdict.WRONG_ANSWER : Verdict.ACCEPTED);
        }
        assertThat(s.peakRunning()).isBetween(2, 4);
        assertThat(s.inFlightCount()).isZero();
    }

    // ------------------------------------------------------------------------------------------------ a backend that goes away

    @Test
    void whenTheBackendProcessDiesTheSandboxDiesWithIt() throws Exception {
        requireWorker();
        List<Process> clients = new CopyOnWriteArrayList<>();
        WorkerLauncher recording = command -> {
            Process p = WorkerLauncher.processBuilder().start(command);
            if (command.contains("--framed")) {
                clients.add(p);
            }
            return p;
        };
        WslBackend backend = new WslBackend(config, recording, new WorkerResponseParser(new ObjectMapper()));
        String jobId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Thread t = new Thread(() -> {
            try {
                backend.execute(jobId, request(LearnerSources.INFINITE_LOOP, ExecutionRequest.Mode.SUBMIT), Duration.ofSeconds(120));
            } catch (InterruptedException ignored) {
                // not expected
            }
        });
        t.start();
        await(() -> !clients.isEmpty(), Duration.ofSeconds(20));
        await(() -> {
            try {
                return sandboxScopes() == 1;
            } catch (Exception e) {
                return false;
            }
        }, Duration.ofSeconds(30));
        // simulate the SpringLab process being killed: the client process vanishes without any cancel call
        clients.get(0).descendants().forEach(ProcessHandle::destroyForcibly);
        clients.get(0).destroyForcibly();
        await(() -> {
            try {
                return sandboxScopes() == 0 && sandboxJvms() == 0;
            } catch (Exception e) {
                return false;
            }
        }, Duration.ofSeconds(20));
        t.join(30_000);
    }

    // ------------------------------------------------------------------------------------------------ the environment is missing something

    @Test
    void aDistroThatDoesNotExistMeansUnavailable() {
        Availability a = new WslBackend(new ExecutionProperties.Wsl("wsl.exe", "NoSuchDistro-" + UUID.randomUUID().toString().substring(0, 6), "/home/x/springlab-judge"),
                new ObjectMapper()).availability();
        assertThat(a.available()).isFalse();
    }

    @Test
    void aMissingJdkMeansUnavailable() {
        requireWorker();
        Availability a = new WslBackend(new ExecutionProperties.Wsl("wsl.exe", distro, "/tmp/no-judge-here-" + UUID.randomUUID().toString().substring(0, 6)),
                new ObjectMapper()).availability();
        assertThat(a.available()).isFalse();
    }

    @Test
    void aJdkWithoutTheWorkerMeansUnavailable() throws Exception {
        requireWorker();
        String dir = "/tmp/judge-no-worker-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            wsl(distro, "mkdir", "-p", dir);
            wsl(distro, "ln", "-s", baseDir + "/jdk17", dir + "/jdk17");
            Availability a = new WslBackend(new ExecutionProperties.Wsl("wsl.exe", distro, dir), new ObjectMapper()).availability();
            assertThat(a.available()).as("java exists, judge.Main does not").isFalse();
        } finally {
            wsl(distro, "rm", "-rf", dir);
        }
    }

    @Test
    void aWorkerDirectoryMissingItsSandboxScriptMeansUnavailable() throws Exception {
        requireWorker();
        String dir = "/tmp/judge-partial-" + UUID.randomUUID().toString().substring(0, 8);
        try {
            wsl(distro, "mkdir", "-p", dir + "/build");
            wsl(distro, "ln", "-s", baseDir + "/jdk17", dir + "/jdk17");
            wsl(distro, "ln", "-s", baseDir + "/build/driver", dir + "/build/driver");
            Availability a = new WslBackend(new ExecutionProperties.Wsl("wsl.exe", distro, dir), new ObjectMapper()).availability();
            assertThat(a.available()).isFalse();
            assertThat(a.reason()).contains("worker file missing");
        } finally {
            wsl(distro, "rm", "-rf", dir);
        }
    }

    @Test
    void aMissingWslExecutableMeansUnavailable() {
        Availability a = new WslBackend(new ExecutionProperties.Wsl("C:\\no\\such\\wsl.exe", "Ubuntu", "/home/x/springlab-judge"), new ObjectMapper()).availability();
        assertThat(a.available()).isFalse();
        assertThat(new File("C:\\no\\such\\wsl.exe")).doesNotExist();
    }
}
