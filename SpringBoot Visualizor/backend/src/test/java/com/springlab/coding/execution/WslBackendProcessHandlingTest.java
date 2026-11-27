package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The process handling of {@link WslBackend}, with REAL operating-system processes (a fake worker JVM) instead of WSL: how the
 * source is sent, what happens on crashes, garbage, huge answers, hangs and cancellation, and how configuration is checked.
 */
class WslBackendProcessHandlingTest {

    private static final String JOB = "0123456789abcdef";
    private static final ExecutionProperties.Wsl CONFIG = new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "/home/someone/springlab-judge");

    /** Starts the fake worker with the behaviour chosen by the test for each worker subcommand, and remembers every process and command. */
    private static final class FakeLauncher implements WorkerLauncher {
        final Map<String, String> behaviour;
        final List<List<String>> commands = new CopyOnWriteArrayList<>();
        final List<Process> processes = new CopyOnWriteArrayList<>();

        FakeLauncher(Map<String, String> behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public Process start(List<String> command) throws java.io.IOException {
            commands.add(command);
            // the worker subcommand is the first argument after "judge.Main"
            String sub = command.get(command.indexOf("judge.Main") + 1);
            String mode = behaviour.getOrDefault(sub, "default");
            String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            Process p = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), FakeWorker.class.getName(), mode).start();
            processes.add(p);
            return p;
        }
    }

    private static WslBackend backend(FakeLauncher launcher) {
        return new WslBackend(CONFIG, launcher, new WorkerResponseParser(new ObjectMapper()));
    }

    private static ExecutionRequest request(String source) {
        return new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT, source.getBytes(StandardCharsets.UTF_8));
    }

    private static ExecutionResult run(WslBackend b, String source) throws Exception {
        return b.execute(JOB, request(source), Duration.ofSeconds(30));
    }

    @Test
    void aValidAnswerBecomesAResultAndTheSourceTravelsOnlyOnStdin() throws Exception {
        FakeLauncher l = new FakeLauncher(Map.of("submit", "echo-length"));
        String source = "public final class RetryExecutor { /* SOURCE-MARKER-7731 */ }";
        ExecutionResult r = run(backend(l), source);
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.tests().get(0).name()).isEqualTo("length " + source.getBytes(StandardCharsets.UTF_8).length);
        for (List<String> command : l.commands) {
            assertThat(String.join(" ", command)).doesNotContain("SOURCE-MARKER-7731");
        }
    }

    @Test
    void theCommandIsBuiltOnlyFromValidatedConfigurationAndAGeneratedJobId() throws Exception {
        FakeLauncher l = new FakeLauncher(Map.of("submit", "accepted"));
        run(backend(l), "class A {}");
        assertThat(l.commands).hasSize(1);
        assertThat(l.commands.get(0)).containsExactly("wsl.exe", "-d", "Ubuntu", "--exec", "/home/someone/springlab-judge/jdk17/bin/java",
                "-Djudge.base=/home/someone/springlab-judge", "-cp", "/home/someone/springlab-judge/build/driver", "judge.Main", "submit", "--framed", "--harness", "RETRY_EXECUTOR", "--job", JOB, "-");
        assertThat(l.commands.get(0)).as("no shell: --exec").doesNotContain("--", "bash", "sh", "-c");
        FakeLauncher runOnly = new FakeLauncher(Map.of("run", "accepted"));
        backend(runOnly).execute(JOB, new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.RUN, "class A {}".getBytes(StandardCharsets.UTF_8)), Duration.ofSeconds(30));
        assertThat(runOnly.commands.get(0)).contains("run").doesNotContain("submit");
    }

    @Test
    void theTestsOfTheRequestTravelOnStdinAfterTheSourceAndNeverOnTheCommandLine() throws Exception {
        FakeLauncher l = new FakeLauncher(Map.of("submit", "echo-tests"));
        var visible = new TestSpec("Shown name", true, 4, 100, 800, List.of("T:a", "R:ok"), "RETURN", "", -1, 2, List.of(100L), "ok");
        var hidden = new TestSpec("HIDDEN-NAME-MARKER", false, 6, 100, 300, List.of("T:a"), "THROW", "TransientException", 0, 1, List.of(), "");
        ExecutionRequest request = new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT,
                "class A {}".getBytes(StandardCharsets.UTF_8), List.of(visible, hidden));
        ExecutionResult r = backend(l).execute(JOB, request, Duration.ofSeconds(30));
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.tests().get(0).name()).isEqualTo("tests 2");
        assertThat(r.tests().get(1).name()).startsWith("TEST ");
        for (List<String> command : l.commands) {
            assertThat(String.join(" ", command)).doesNotContain("HIDDEN-NAME-MARKER").doesNotContain("Shown name");
            assertThat(String.join(" ", command)).doesNotContain(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("HIDDEN-NAME-MARKER".getBytes(StandardCharsets.UTF_8)));
        }
    }

    @Test
    void aRequestWithoutTestsSendsAnEmptyTestsBlock() throws Exception {
        ExecutionResult r = run(backend(new FakeLauncher(Map.of("submit", "echo-tests"))), "class A {}");
        assertThat(r.tests().get(0).name()).isEqualTo("tests 0");
    }

    @Test
    void stdinStaysOpenAfterTheSourceSoTheWorkerCanTellWhenTheCallerIsGone() throws Exception {
        ExecutionResult r = run(backend(new FakeLauncher(Map.of("submit", "stdin-open"))), "class A {}");
        assertThat(r.verdict()).as("the fake reports INTERNAL_ERROR if it saw EOF on stdin").isEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void aWorkerThatCrashesMidAnswerIsAnInternalError() throws Exception {
        assertThat(run(backend(new FakeLauncher(Map.of("submit", "crash"))), "class A {}").verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void aValidAnswerFromAWorkerThatExitsWithAnErrorIsNotTrusted() throws Exception {
        assertThat(run(backend(new FakeLauncher(Map.of("submit", "crash-after-valid-answer"))), "class A {}").verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void garbageEmptyAndOversizedAnswersAreInternalErrors() throws Exception {
        for (String mode : List.of("garbage", "empty", "huge", "false-accept")) {
            assertThat(run(backend(new FakeLauncher(Map.of("submit", mode))), "class A {}").verdict()).as(mode).isEqualTo(Verdict.INTERNAL_ERROR);
        }
    }

    @Test
    void aWorkerThatCannotBeStartedIsAnInternalError() throws Exception {
        WorkerLauncher failing = command -> {
            throw new java.io.IOException("cannot run program");
        };
        WslBackend b = new WslBackend(CONFIG, failing, new WorkerResponseParser(new ObjectMapper()));
        assertThat(b.execute(JOB, request("class A {}"), Duration.ofSeconds(5)).verdict()).isEqualTo(Verdict.INTERNAL_ERROR);
    }

    @Test
    void aHiddenTestMessageFromTheWorkerNeverLeavesTheBackend() throws Exception {
        ExecutionResult r = run(backend(new FakeLauncher(Map.of("submit", "hidden-leak"))), "class A {}");
        assertThat(r.verdict()).isEqualTo(Verdict.WRONG_ANSWER);
        assertThat(r.tests().get(0).message()).isNull();
        assertThat(r.toString()).doesNotContain("secret value 42");
    }

    @Test
    void aFloodOnStderrDoesNotBlockTheWorkerOrBecomeAResult() throws Exception {
        ExecutionResult r = run(backend(new FakeLauncher(Map.of("submit", "stderr-flood"))), "class A {}");
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.toString()).doesNotContain("eeeeeeee");
    }

    @Test
    void theHardTimeoutIsTimeLimitAndTheWholeClientProcessIsKilled() throws Exception {
        FakeLauncher l = new FakeLauncher(Map.of("submit", "hang", "cancel", "cancel"));
        long started = System.nanoTime();
        ExecutionResult r = backend(l).execute(JOB, request("class A {}"), Duration.ofSeconds(2));
        assertThat(r.verdict()).isEqualTo(Verdict.TIME_LIMIT);
        assertThat(Duration.ofNanos(System.nanoTime() - started).toSeconds()).isLessThan(15);
        assertThat(l.commands.stream().anyMatch(c -> c.contains("cancel") && c.contains(JOB))).as("the sandbox scope is cancelled too").isTrue();
        Process hung = l.processes.get(0);
        assertThat(hung.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void cancelKillsTheSandboxScopeAndTheClientProcess() throws Exception {
        FakeLauncher l = new FakeLauncher(Map.of("submit", "hang", "cancel", "cancel"));
        WslBackend b = backend(l);
        AtomicReference<ExecutionResult> result = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                result.set(b.execute(JOB, request("class A {}"), Duration.ofSeconds(120)));
            } catch (InterruptedException ignored) {
                // not expected here
            }
        });
        t.start();
        long end = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (l.processes.isEmpty() && System.nanoTime() < end) {
            Thread.sleep(20);
        }
        Thread.sleep(500);
        b.cancel(JOB);
        t.join(15_000);
        assertThat(t.isAlive()).isFalse();
        assertThat(l.commands.stream().anyMatch(c -> c.contains("cancel") && c.contains(JOB))).isTrue();
        assertThat(l.processes.get(0).isAlive()).isFalse();
        assertThat(result.get().verdict()).as("a killed worker never yields a pass").isNotEqualTo(Verdict.ACCEPTED);
    }

    @Test
    void interruptingTheCallerKillsTheSandboxToo() throws Exception {
        FakeLauncher l = new FakeLauncher(Map.of("submit", "hang", "cancel", "cancel"));
        WslBackend b = backend(l);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                b.execute(JOB, request("class A {}"), Duration.ofSeconds(120));
            } catch (Throwable e) {
                thrown.set(e);
            }
        });
        t.start();
        Thread.sleep(1000);
        t.interrupt();
        t.join(15_000);
        assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
        assertThat(l.processes.get(0).isAlive()).isFalse();
        assertThat(l.commands.stream().anyMatch(c -> c.contains("cancel"))).isTrue();
    }

    @Test
    void cancelForAJobThatIsNotRunningDoesNotStartAnything() {
        FakeLauncher l = new FakeLauncher(Map.of());
        backend(l).cancel(JOB);
        backend(l).cancel("not-a-job-id; rm -rf /");
        assertThat(l.commands).isEmpty();
    }

    @Test
    void aMalformedJobIdIsRefusedBeforeAnythingIsStarted() {
        FakeLauncher l = new FakeLauncher(Map.of());
        for (String bad : new String[]{null, "", "xyz", "0123456789ABCDEF", "0123456789abcde", "0123456789abcdef0", "../../etc/passwd", "0123456789abcde;"}) {
            assertThatThrownBy(() -> backend(l).execute(bad, request("class A {}"), Duration.ofSeconds(5))).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(l.commands).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ availability

    @Test
    void aHealthyWorkerCheckMakesTheBackendAvailableAndIsCached() {
        FakeLauncher l = new FakeLauncher(Map.of("check", "check-ok"));
        WslBackend b = backend(l);
        assertThat(b.availability().available()).isTrue();
        assertThat(b.availability().available()).isTrue();
        assertThat(l.commands).as("the second call is served from the cache").hasSize(1);
        assertThat(l.commands.get(0)).contains("check");
    }

    @Test
    void aWorkerThatReportsAProblemMakesTheBackendUnavailableWithItsReason() {
        Availability a = backend(new FakeLauncher(Map.of("check", "check-bad"))).availability();
        assertThat(a.available()).isFalse();
        assertThat(a.reason()).contains("worker file missing");
    }

    @Test
    void garbageOrAFailingCheckMeansUnavailable() {
        assertThat(backend(new FakeLauncher(Map.of("check", "check-garbage"))).availability().available()).isFalse();
        assertThat(backend(new FakeLauncher(Map.of("check", "check-exit"))).availability().available()).isFalse();
    }

    @Test
    void wslThatCannotBeStartedMeansUnavailable() {
        WorkerLauncher failing = command -> {
            throw new java.io.IOException("The system cannot find the file specified");
        };
        Availability a = new WslBackend(CONFIG, failing, new WorkerResponseParser(new ObjectMapper())).availability();
        assertThat(a.available()).isFalse();
        assertThat(a.reason()).doesNotContain("The system cannot find");
    }

    @Test
    void anExecutionServiceOverAnUnavailableWslBackendRefusesWith503AndRunsNothing() {
        FakeLauncher l = new FakeLauncher(Map.of("check", "check-bad"));
        ExecutionService s = new ExecutionService(backend(l), new ExecutionProperties(ExecutionProperties.Backend.WSL, 4, 4, 60, CONFIG));
        try {
            assertThatThrownBy(() -> s.submit("ann", request("class A {}"))).isInstanceOf(ExecutionUnavailableException.class);
            assertThat(l.commands.stream().filter(c -> c.contains("submit") || c.contains("run")).count()).isZero();
        } finally {
            s.shutdown();
        }
    }

    // ------------------------------------------------------------------------------------------------ configuration is validated

    @Test
    void unsafeOrMissingConfigurationMakesTheBackendUnavailableAndNeverStartsAProcess() {
        List<ExecutionProperties.Wsl> bad = new ArrayList<>();
        bad.add(new ExecutionProperties.Wsl("wsl.exe", null, null));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", null));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", null, "/home/x/judge"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu; calc", "/home/x/judge"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "-e", "/home/x/judge"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "relative/path"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "/home/x/../../etc"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "/home/x/judge dir"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "/home/x/judge$(id)"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "/home/x/judge/"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe", "Ubuntu", "/home//judge"));
        bad.add(new ExecutionProperties.Wsl("wsl.exe\"&calc", "Ubuntu", "/home/x/judge"));
        bad.add(new ExecutionProperties.Wsl("", "Ubuntu", "/home/x/judge"));
        FakeLauncher l = new FakeLauncher(Map.of("check", "check-ok"));
        for (ExecutionProperties.Wsl cfg : bad) {
            WslBackend b = new WslBackend(cfg, l, new WorkerResponseParser(new ObjectMapper()));
            assertThat(b.availability().available()).as(String.valueOf(cfg)).isFalse();
            assertThatThrownBy(() -> b.execute(JOB, request("class A {}"), Duration.ofSeconds(5))).isInstanceOf(ExecutionUnavailableException.class);
        }
        assertThat(l.commands).isEmpty();
    }

    @Test
    void theWorkerEnvironmentHoldsNoSettingOfThisApplication() {
        Map<String, String> parent = Map.of("JWT_SECRET", "s3cret", "DB_PASSWORD", "pw", "DB_USERNAME", "root", "ADMIN_PASSWORD", "admin-pw",
                "SPRING_DATASOURCE_PASSWORD", "x", "AWS_SECRET_ACCESS_KEY", "k", "SystemRoot", "C:\\Windows", "PATH", "C:\\Windows\\System32", "TEMP", "C:\\Temp");
        Map<String, String> kept = WorkerLauncher.minimalEnvironment(parent);
        assertThat(kept).containsOnlyKeys("SystemRoot", "PATH", "TEMP");
        assertThat(kept.toString()).doesNotContain("s3cret").doesNotContain("pw");
    }
}
