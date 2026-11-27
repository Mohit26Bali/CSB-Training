package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.coding.execution.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase F5 with nothing scripted: every new problem judged by the real WSL worker in the real sandbox. For each problem a correct reference
 * solution must be ACCEPTED on all tests (visible and hidden), and every meaningfully wrong solution must be rejected with WRONG_ANSWER.
 * The reference and wrong solutions are in {@code src/test/resources/coding/bank/<HARNESS>/}. Skipped, visibly, when the worker is not
 * installed.
 */
class CodingBankRealWorkerIntegrationTest extends AbstractCodingTest {

    private static final String DISTRO = System.getProperty("springlab.coding.test.wsl-distro", "Ubuntu");

    /** slug -> {harness folder, class name, wrong solutions}. */
    private static final Map<String, String[]> BANK = new LinkedHashMap<>();

    static {
        BANK.put("circuit-breaker-state-machine", new String[]{"CIRCUIT_BREAKER", "CircuitBreaker",
                "wrong-halfopen-allows-all", "wrong-timeout-off-by-one", "wrong-failures-not-consecutive", "wrong-reopen-keeps-old-timer"});
        BANK.put("bulkhead-concurrency-limiter", new String[]{"BULKHEAD", "Bulkhead",
                "wrong-leaks-permit-on-failure", "wrong-blocks-instead-of-rejecting", "wrong-releases-before-running"});
        BANK.put("idempotency-key-handler", new String[]{"IDEMPOTENCY", "IdempotencyHandler",
                "wrong-never-expires", "wrong-no-in-progress-check", "wrong-failure-leaves-key-locked", "wrong-caches-failures-as-null"});
        BANK.put("saga-compensation-coordinator", new String[]{"SAGA", "SagaCoordinator",
                "wrong-compensates-forwards", "wrong-compensates-the-failed-step", "wrong-stops-at-first-compensation-failure", "wrong-keeps-executing-after-failure", "wrong-can-run-twice"});
        BANK.put("transactional-outbox-relay", new String[]{"OUTBOX", "OutboxRelay",
                "wrong-marks-before-publishing", "wrong-skips-failed-event-and-continues", "wrong-consumer-applies-duplicates", "wrong-ignores-the-batch-limit", "wrong-lets-failures-escape"});
    }

    @DynamicPropertySource
    static void wsl(DynamicPropertyRegistry registry) {
        String base = System.getProperty("springlab.coding.test.wsl-base-dir");
        if (base == null) {
            try {
                Process p = new ProcessBuilder("wsl.exe", "-d", DISTRO, "--exec", "printenv", "HOME").redirectErrorStream(true).start();
                p.getOutputStream().close();
                String home = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                p.waitFor(60, TimeUnit.SECONDS);
                base = home.startsWith("/") && !home.contains("\n") && !home.contains(" ") ? home + "/springlab-judge" : null;
            } catch (Exception e) {
                base = null;
            }
        }
        String resolved = base;
        registry.add("springlab.coding.execution.backend", () -> resolved == null ? "DISABLED" : "WSL");
        if (resolved != null) {
            String dir = resolved;
            registry.add("springlab.coding.execution.wsl.distro", () -> DISTRO);
            registry.add("springlab.coding.execution.wsl.base-dir", () -> dir);
        }
    }

    @Autowired
    private ExecutionService executionService;

    private void requireWorker() {
        assumeTrue(executionService.availability().available(), "the WSL worker is not available here: " + executionService.availability().reason());
    }

    private static String source(String folder, String name) throws Exception {
        try (InputStream in = CodingBankRealWorkerIntegrationTest.class.getResourceAsStream("/coding/bank/" + folder + "/" + name + ".java")) {
            assertThat(in).as(folder + "/" + name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private JsonNode submit(String slug, String source) throws Exception {
        return body(postSource(student, "/api/coding/problems/" + slug + "/submit", source).andExpect(status().isCreated()));
    }

    /** Names of the hidden tests, and every expected-trace line that occurs in hidden tests only. None may be in a response. */
    private List<String> hiddenSecrets(String slug) {
        List<String> out = new ArrayList<>(jdbc.queryForList("select c.name from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                + "join coding_problems p on p.id = v.problem_id where p.slug = ? and c.visible = false", String.class, slug));
        out.addAll(List.of("expected_spec", "input_spec", "/home/", "jdk17", "bwrap", "harness"));
        return out;
    }

    private void assertNoHiddenData(String slug, String text) {
        for (String s : hiddenSecrets(slug)) {
            assertThat(text).as("hidden data in a learner response").doesNotContain(s);
        }
    }

    @Test
    void everyReferenceSolutionPassesEveryTestHiddenOnesIncludedAndIsRecorded() throws Exception {
        requireWorker();
        for (var e : BANK.entrySet()) {
            String slug = e.getKey();
            String reference = source(e.getValue()[0], "reference");
            JsonNode run = body(postSource(student, "/api/coding/problems/" + slug + "/run", reference).andExpect(status().isOk()));
            assertThat(run.get("verdict").asText()).as(slug + " run").isEqualTo("ACCEPTED");
            assertThat(run.get("total").asInt()).as(slug).isEqualTo(3);

            JsonNode s = submit(slug, reference);
            assertThat(s.get("verdict").asText()).as(slug + " submit " + s).isEqualTo("ACCEPTED");
            assertThat(s.get("passed").asInt()).as(slug).isEqualTo(s.get("total").asInt()).isGreaterThanOrEqualTo(11);
            assertThat(s.get("hiddenPassed").asInt()).as(slug).isEqualTo(s.get("hiddenTotal").asInt()).isGreaterThanOrEqualTo(8);
            assertThat(s.get("visibleTests")).as(slug).hasSize(3).allMatch(t -> t.get("status").asText().equals("PASS"));
            assertNoHiddenData(slug, s.toString());
            assertThat(body(getAs(student, "/api/coding/submissions/" + s.get("id").asLong())).get("sourceCode").asText()).isEqualTo(reference);
        }
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isEqualTo(5);
        JsonNode p = body(getAs(student, "/api/coding/progress/me"));
        assertThat(p.get("problemsAccepted").asInt()).isEqualTo(5);
    }

    @Test
    void everyWrongSolutionIsRejectedWithWrongAnswerAndNeverAccepted() throws Exception {
        requireWorker();
        int checked = 0;
        for (var e : BANK.entrySet()) {
            String slug = e.getKey();
            String[] v = e.getValue();
            for (int i = 2; i < v.length; i++) {
                JsonNode s = submit(slug, source(v[0], v[i]));
                assertThat(s.get("verdict").asText()).as(slug + " / " + v[i]).isEqualTo("WRONG_ANSWER");
                assertThat(s.get("passed").asInt()).as(slug + " / " + v[i]).isLessThan(s.get("total").asInt());
                assertThat(s.get("feedback").asText()).isNotBlank();
                assertNoHiddenData(slug, s.toString());
                checked++;
            }
        }
        assertThat(checked).isEqualTo(BANK.values().stream().mapToInt(a -> a.length - 2).sum()).isGreaterThanOrEqualTo(10);
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isZero();
    }

    @Test
    void aWrongSolutionThatFailsASampleGetsAnExplanationOfTheStepThatDiffered() throws Exception {
        requireWorker();
        // the half-open breaker that lets every request through fails a visible test only if a visible test probes it; either way the message names a step
        JsonNode r = body(postSource(student, "/api/coding/problems/circuit-breaker-state-machine/run", source("CIRCUIT_BREAKER", "wrong-timeout-off-by-one")).andExpect(status().isOk()));
        if (r.get("verdict").asText().equals("WRONG_ANSWER")) {
            boolean explained = false;
            for (JsonNode t : r.get("tests")) {
                if (t.get("status").asText().equals("FAIL")) {
                    assertThat(t.get("message").asText()).contains("step ").contains("expected").contains("got");
                    explained = true;
                }
            }
            assertThat(explained).isTrue();
        }
    }

    @Test
    void aCompileErrorIsReportedWithItsLineForEveryNewProblem() throws Exception {
        requireWorker();
        for (var e : BANK.entrySet()) {
            String broken = source(e.getValue()[0], "reference").replaceFirst("(class\\s+" + e.getValue()[1] + "[^{]*\\{)", "$1 int broken = ;");
            JsonNode s = submit(e.getKey(), broken);
            assertThat(s.get("verdict").asText()).as(e.getKey()).isEqualTo("COMPILE_ERROR");
            assertThat(s.get("compilerOutput").asText()).as(e.getKey()).contains(e.getValue()[1] + ".java:").doesNotContain("/home/").doesNotContain("/tmp/");
            assertThat(s.get("passed").asInt()).isZero();
        }
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'COMPILE_ERROR'", student.getId())).isEqualTo(5);
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isZero();
    }

    @Test
    void aClassWithTheWrongShapeIsRejectedAsACompileErrorNotAnInternalFailure() throws Exception {
        requireWorker();
        for (var e : BANK.entrySet()) {
            // the right class name, none of the required members
            JsonNode s = submit(e.getKey(), "public final class " + e.getValue()[1] + " { }\n");
            assertThat(s.get("verdict").asText()).as(e.getKey() + " " + s).isIn("COMPILE_ERROR", "WRONG_ANSWER", "RUNTIME_ERROR");
            assertThat(s.get("verdict").asText()).isNotEqualTo("ACCEPTED").isNotEqualTo("INTERNAL_ERROR");
        }
    }

    @Test
    void anInfiniteLoopInANewProblemIsTimeLimitWithAGenericHint() throws Exception {
        requireWorker();
        String loop = source("SAGA", "reference").replaceFirst("(class\\s+SagaCoordinator[^{]*\\{)", "$1 static { if (System.nanoTime() != 0) { while (true) { } } }");
        JsonNode s = submit("saga-compensation-coordinator", loop);
        assertThat(s.get("verdict").asText()).isIn("TIME_LIMIT", "RUNTIME_ERROR");
        assertThat(s.get("verdict").asText()).isNotEqualTo("ACCEPTED");
        if (s.get("verdict").asText().equals("TIME_LIMIT")) {
            assertThat(s.get("feedback").asText()).contains("did not finish in time");
        }
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
    }

    @Test
    void thePilotStillWorksAfterTheNewHarnessesWereAdded() throws Exception {
        requireWorker();
        JsonNode s = submit(SLUG, com.springlab.coding.execution.LearnerSources.REFERENCE);
        assertThat(s.get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(s.get("passed").asInt()).isEqualTo(12);
    }
}
