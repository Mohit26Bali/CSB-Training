package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.coding.execution.ExecutionService;
import com.springlab.coding.execution.LearnerSources;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The whole path with nothing scripted: HTTP request, authentication, test selection from the database, the bounded queue, the real WSL
 * worker, the real sandbox, verdict reconciliation, persistence and progress. Skipped, visibly and with the reason, when the WSL worker is
 * not installed on the machine running the tests.
 */
class CodingRealWorkerIntegrationTest extends AbstractCodingTest {

    private static final String DISTRO = System.getProperty("springlab.coding.test.wsl-distro", "Ubuntu");

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

    private JsonNode submit(User u, String source) throws Exception {
        return body(postSource(u, "/api/coding/problems/" + SLUG + "/submit", source).andExpect(status().isCreated()));
    }

    private List<String> hiddenNames() {
        return jdbc.queryForList("select name from coding_test_cases where visible = false", String.class);
    }

    private void assertNoHiddenData(String text) {
        for (String n : hiddenNames()) {
            assertThat(text).doesNotContain(n);
        }
        assertThat(text).doesNotContain("2305843009213693952").doesNotContain("1099511627776").doesNotContain("expected_spec").doesNotContain("/home/").doesNotContain("jdk17").doesNotContain("bwrap");
    }

    @Test
    void aCorrectSolutionPassesEveryTestIncludingTheHiddenOnesAndIsRecorded() throws Exception {
        requireWorker();
        JsonNode run = body(postSource(student, "/api/coding/problems/" + SLUG + "/run", LearnerSources.REFERENCE).andExpect(status().isOk()));
        assertThat(run.get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(run.get("total").asInt()).isEqualTo(3);
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).as("a run is not recorded").isZero();

        JsonNode s = submit(student, LearnerSources.REFERENCE);
        assertThat(s.get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(s.get("passed").asInt()).isEqualTo(12);
        assertThat(s.get("hiddenPassed").asInt()).isEqualTo(9);
        assertThat(s.get("visibleTests")).hasSize(3).allMatch(t -> t.get("status").asText().equals("PASS"));
        assertNoHiddenData(s.toString());
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isEqualTo(1);
        assertThat(body(getAs(student, "/api/coding/submissions/" + s.get("id").asLong())).get("sourceCode").asText()).isEqualTo(LearnerSources.REFERENCE);
    }

    @Test
    void theDatabaseDecidesTheTestsNotTheWorkersBuiltInSet() throws Exception {
        requireWorker();
        // make one hidden expectation wrong in the database: the correct solution must now fail exactly that test
        long id = jdbc.queryForObject("select t.id from coding_test_cases t where t.name = 'Delay is capped at the maximum'", Long.class);
        String original = jdbc.queryForObject("select expected_spec from coding_test_cases where id = ?", String.class, id);
        jdbc.update("update coding_test_cases set expected_spec = replace(expected_spec, '[100, 200, 300, 300, 300]', '[100, 200, 400, 800, 800]') where id = ?", id);
        try {
            JsonNode s = submit(student, LearnerSources.REFERENCE);
            assertThat(s.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
            assertThat(s.get("passed").asInt()).isEqualTo(11);
            assertThat(s.get("hiddenPassed").asInt()).isEqualTo(8);
            assertThat(s.get("feedback").asText()).contains("1 hidden test(s) fail");
            assertNoHiddenData(s.toString());
        } finally {
            jdbc.update("update coding_test_cases set expected_spec = ? where id = ?", original, id);
        }
        assertThat(submit(student, LearnerSources.REFERENCE).get("verdict").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void aSolutionThatFailsOnlyHiddenTestsIsWrongAnswerWithAGenericHint() throws Exception {
        requireWorker();
        JsonNode s = submit(student, LearnerSources.WRONG);
        assertThat(s.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(s.get("passed").asInt()).isLessThan(12);
        assertThat(s.get("visibleTests").size() + s.get("hiddenTotal").asInt()).isEqualTo(12);
        assertThat(s.get("feedback").asText()).contains("hidden test(s) fail");
        assertNoHiddenData(s.toString());
        JsonNode p = body(getAs(student, "/api/coding/progress/me"));
        assertThat(p.get("problemsAccepted").asInt()).isZero();
        assertThat(p.get("totalFullSubmissions").asInt()).isEqualTo(1);
    }

    @Test
    void aSolutionThatFailsASampleTestGetsTheSampleMessage() throws Exception {
        requireWorker();
        JsonNode r = body(postSource(student, "/api/coding/problems/" + SLUG + "/run", LearnerSources.NEVER_RETRIES).andExpect(status().isOk()));
        assertThat(r.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(r.get("tests").get(1).get("status").asText()).isEqualTo("FAIL");
        assertThat(r.get("tests").get(1).get("message").asText()).isNotBlank();
        assertThat(r.get("feedback").asText()).contains("sample tests fail");
    }

    @Test
    void aCompileErrorIsReportedWithItsLine() throws Exception {
        requireWorker();
        JsonNode s = submit(student, LearnerSources.COMPILE_ERROR);
        assertThat(s.get("verdict").asText()).isEqualTo("COMPILE_ERROR");
        assertThat(s.get("compilerOutput").asText()).contains("RetryExecutor.java:").doesNotContain("/home/").doesNotContain("/tmp/").doesNotContain("/src/");
        assertThat(s.get("passed").asInt()).isZero();
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isZero();
    }

    @Test
    void anInfiniteLoopIsTimeLimitAndNeverAccepted() throws Exception {
        requireWorker();
        JsonNode s = submit(student, LearnerSources.INFINITE_LOOP);
        assertThat(s.get("verdict").asText()).isEqualTo("TIME_LIMIT");
        assertThat(s.get("feedback").asText()).contains("did not finish in time");
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
    }

    @Test
    void aProgramThatEndsTheJvmIsARuntimeError() throws Exception {
        requireWorker();
        assertThat(submit(student, LearnerSources.EXIT).get("verdict").asText()).isEqualTo("RUNTIME_ERROR");
    }

    @Test
    void anExceptionThrownFromExecuteIsObservedBehaviourAndFailsTheTestsThatExpectedSomethingElse() throws Exception {
        requireWorker();
        String throwing = LearnerSources.REFERENCE.replace("if (maxAttempts < 1)", "if (true) throw new RuntimeException(\"boom\"); if (maxAttempts < 1)");
        JsonNode s = submit(student, throwing);
        // the harness records the exception as what execute did; it is a wrong behaviour, not a crash of the program
        assertThat(s.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(s.get("visibleTests").get(0).get("message").asText()).contains("RuntimeException");
    }

    @Test
    void aHeapBombIsMemoryLimit() throws Exception {
        requireWorker();
        assertThat(submit(student, LearnerSources.HEAP_BOMB).get("verdict").asText()).isEqualTo("MEMORY_LIMIT");
    }

    @Test
    void fourUsersSubmittingAtOnceAreAllJudgedAndRecordedSeparately() throws Exception {
        requireWorker();
        List<User> four = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            four.add(newUser(USER_PREFIX + "par" + i + "_" + System.nanoTime(), Role.STUDENT));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        List<Future<JsonNode>> results = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            User u = four.get(i);
            String source = i == 3 ? LearnerSources.WRONG : LearnerSources.REFERENCE;
            results.add(pool.submit(() -> submit(u, source)));
        }
        for (int i = 0; i < 4; i++) {
            assertThat(results.get(i).get().get("verdict").asText()).isEqualTo(i == 3 ? "WRONG_ANSWER" : "ACCEPTED");
        }
        pool.shutdown();
        for (int i = 0; i < 4; i++) {
            assertThat(count("select count(*) from coding_submissions where user_id = ?", four.get(i).getId())).isEqualTo(1);
            assertThat(body(getAs(four.get(i), "/api/coding/progress/me")).get("problemsAccepted").asInt()).isEqualTo(i == 3 ? 0 : 1);
        }
    }
}
