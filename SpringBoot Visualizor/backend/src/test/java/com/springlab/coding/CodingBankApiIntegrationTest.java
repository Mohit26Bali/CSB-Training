package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.coding.execution.ExecutionRequest;
import com.springlab.coding.execution.ExecutionResult;
import com.springlab.coding.execution.ScriptTestSpec;
import com.springlab.coding.execution.Verdict;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase F5 through the API with a scripted worker ANSWER (the real worker is exercised in {@link CodingBankRealWorkerIntegrationTest}):
 * the six problems in the catalogue, the detail page of each, the harness the BACKEND picks from the database, hidden-test secrecy for the
 * scripted problems, and the handling of a worker answer that does not agree with the tests that were sent.
 */
class CodingBankApiIntegrationTest extends AbstractCodingTest {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedBackend scriptedBackend() {
            return new ScriptedBackend();
        }
    }

    /** slug -> harness kind and the class a learner must write. */
    private static final Map<String, String[]> BANK = new java.util.LinkedHashMap<>();

    static {
        BANK.put("circuit-breaker-state-machine", new String[]{"CIRCUIT_BREAKER", "CircuitBreaker"});
        BANK.put("bulkhead-concurrency-limiter", new String[]{"BULKHEAD", "Bulkhead"});
        BANK.put("idempotency-key-handler", new String[]{"IDEMPOTENCY", "IdempotencyHandler"});
        BANK.put("saga-compensation-coordinator", new String[]{"SAGA", "SagaCoordinator"});
        BANK.put("transactional-outbox-relay", new String[]{"OUTBOX", "OutboxRelay"});
    }

    @Autowired
    private ScriptedBackend backend;

    @BeforeEach
    void fresh() {
        backend.reset();
    }

    private static final String SOME = "public final class Anything { }\n";

    @Test
    void theCatalogueListsTheSixProblemsOfTheMicroservicesTopic() throws Exception {
        JsonNode list = body(getAs(student, "/api/coding/problems?topic=microservices").andExpect(status().isOk()));
        assertThat(list).hasSize(6);
        List<String> slugs = new ArrayList<>();
        list.forEach(n -> slugs.add(n.get("slug").asText()));
        assertThat(slugs).contains(SLUG).containsAll(BANK.keySet());
        for (JsonNode n : list) {
            assertThat(n.get("topicSlug").asText()).isEqualTo("microservices");
            assertThat(n.get("language").asText()).isEqualTo("java17");
            assertThat(n.get("difficulty").asText()).isIn("EASY", "MEDIUM", "HARD");
            assertThat(n.get("title").asText()).isNotBlank();
            assertThat(n.get("summary").asText()).isNotBlank();
            assertThat(n.get("version").asInt()).isEqualTo(1);
            assertThat(n.get("accepted").asBoolean()).isFalse();
        }
        // nothing a learner receives names the harness kind
        assertThat(list.toString()).doesNotContain("harness").doesNotContain("CIRCUIT_BREAKER").doesNotContain("OUTBOX");
    }

    @Test
    void eachNewProblemOpensWithStatementObjectivesConstraintsExamplesStarterCodeAndThreeSamples() throws Exception {
        for (var e : BANK.entrySet()) {
            JsonNode d = body(getAs(student, "/api/coding/problems/" + e.getKey()).andExpect(status().isOk()));
            String slug = e.getKey();
            assertThat(d.get("slug").asText()).isEqualTo(slug);
            assertThat(d.get("topicSlug").asText()).isEqualTo("microservices");
            assertThat(d.get("language").asText()).isEqualTo("java17");
            assertThat(d.get("difficulty").asText()).isIn("EASY", "MEDIUM", "HARD");
            assertThat(d.get("statement").asText().length()).as(slug).isGreaterThan(400);
            assertThat(d.get("objectives").size()).as(slug).isGreaterThanOrEqualTo(3);
            assertThat(d.get("constraints").asText()).as(slug).isNotBlank();
            assertThat(d.get("examples").size()).as(slug).isGreaterThanOrEqualTo(2);
            for (JsonNode ex : d.get("examples")) {
                assertThat(ex.get("title").asText()).isNotBlank();
                assertThat(ex.get("input").asText()).isNotBlank();
                assertThat(ex.get("output").asText()).isNotBlank();
                assertThat(ex.get("explanation").asText()).isNotBlank();
            }
            // the learner is told what is simplified: the statement says so in its own words
            assertThat(d.get("statement").asText().toLowerCase()).as(slug).containsAnyOf("simplif", "simulat", "not a real", "deterministic");
            // the starter code declares the class the worker expects, and compiles as a stub (it is not the solution)
            assertThat(d.get("starterCode").asText()).as(slug).contains("class " + e.getValue()[1]);
            assertThat(d.get("sampleTests")).as(slug).hasSize(3);
            for (JsonNode s : d.get("sampleTests")) {
                assertThat(s.get("name").asText()).isNotBlank();
                assertThat(s.get("input").asText()).startsWith("1. ").contains("\n");
                assertThat(s.get("expected").asText()).startsWith("1. ");
            }
            assertThat(d.get("hiddenTestCount").asInt()).as(slug).isGreaterThanOrEqualTo(6);
            assertThat(d.toString()).as(slug).doesNotContain("harness").doesNotContain("CIRCUIT_BREAKER").doesNotContain("input_spec").doesNotContain("expected_spec");
        }
    }

    @Test
    void theDetailOfANewProblemHoldsNoHiddenTestData() throws Exception {
        for (String slug : BANK.keySet()) {
            String detail = text(getAs(student, "/api/coding/problems/" + slug).andExpect(status().isOk()));
            for (var row : jdbc.queryForList("select c.name from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                    + "join coding_problems p on p.id = v.problem_id where p.slug = ? and c.visible = false", slug)) {
                assertThat(detail).as(slug).doesNotContain((String) row.get("name"));
            }
        }
    }

    @Test
    void theBackendPicksTheHarnessFromTheDatabaseAndALearnerCannotChooseIt() throws Exception {
        for (var e : BANK.entrySet()) {
            backend.reset();
            // the learner tries to name another harness, another problem and its own tests; none of it is read
            String forged = objectMapper.writeValueAsString(Map.of("sourceCode", SOME, "harness", "RETRY_EXECUTOR", "harnessKind", "SAGA", "problem", "RETRY_EXECUTOR",
                    "tests", List.of(Map.of("name", "mine", "visible", true))));
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/coding/problems/" + e.getKey() + "/submit")
                    .header("Authorization", bearer(student)).contentType("application/json").content(forged)).andExpect(status().isCreated());
            ExecutionRequest sent = backend.last;
            assertThat(sent.problem().name()).as(e.getKey()).isEqualTo(e.getValue()[0]);
            assertThat(sent.tests()).as(e.getKey()).isNotEmpty().allMatch(t -> t instanceof ScriptTestSpec);
            assertThat(sent.tests().stream().map(t -> t.name())).as(e.getKey()).doesNotContain("mine");
            long stored = count("select count(*) from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id join coding_problems p on p.id = v.problem_id where p.slug = ?", e.getKey());
            assertThat(sent.tests()).as(e.getKey()).hasSize((int) stored);
            purge(student);
        }
    }

    @Test
    void aRunSendsOnlyTheThreeVisibleTestsAndStoresNothing() throws Exception {
        for (String slug : BANK.keySet()) {
            backend.reset();
            JsonNode r = body(postSource(student, "/api/coding/problems/" + slug + "/run", SOME).andExpect(status().isOk()));
            assertThat(r.get("verdict").asText()).isEqualTo("ACCEPTED");
            assertThat(r.get("total").asInt()).as(slug).isEqualTo(3);
            assertThat(backend.last.tests()).as(slug).hasSize(3).allMatch(t -> t.visible());
            assertThat(backend.last.mode()).isEqualTo(ExecutionRequest.Mode.RUN);
        }
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isZero();
        assertThat(count("select count(*) from coding_progress where user_id = ?", student.getId())).isZero();
    }

    @Test
    void aSubmissionRecordsAllTestsCountsProgressAndHidesTheHiddenOnes() throws Exception {
        for (String slug : BANK.keySet()) {
            JsonNode s = body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME).andExpect(status().isCreated()));
            assertThat(s.get("verdict").asText()).isEqualTo("ACCEPTED");
            assertThat(s.get("passed").asInt()).isEqualTo(s.get("total").asInt());
            assertThat(s.get("visibleTests")).as(slug).hasSize(3);
            assertThat(s.get("hiddenTotal").asInt()).as(slug).isEqualTo(s.get("total").asInt() - 3);
            assertThat(s.get("problemVersion").asInt()).isEqualTo(1);
        }
        JsonNode p = body(getAs(student, "/api/coding/progress/me"));
        assertThat(p.get("problemsAccepted").asInt()).isEqualTo(5);
        assertThat(p.get("totalFullSubmissions").asInt()).isEqualTo(5);
    }

    @Test
    void aFailedHiddenTestOfANewProblemNeverLeaksItsNameOrItsExpectedValues() throws Exception {
        backend.script = (id, req) -> ScriptedBackend.hiddenFails(req);
        for (String slug : BANK.keySet()) {
            String text = text(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME).andExpect(status().isCreated()));
            assertThat(text).as(slug).contains("WRONG_ANSWER").doesNotContain("LEAKED-HIDDEN-DETAIL").doesNotContain("expected_spec").doesNotContain("harness");
            for (var row : jdbc.queryForList("select c.name from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                    + "join coding_problems p on p.id = v.problem_id where p.slug = ? and c.visible = false", slug)) {
                assertThat(text).as(slug).doesNotContain((String) row.get("name"));
            }
        }
        assertThat(count("select count(*) from coding_submission_results r join coding_submissions s on s.id = r.submission_id where s.user_id = ? and r.message is not null", student.getId())).isZero();
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isZero();
    }

    @Test
    void aWorkerAnswerThatDoesNotAgreeWithTheScriptedTestsIsNeverAnAcceptance() throws Exception {
        for (String slug : BANK.keySet()) {
            // ACCEPTED with a single test instead of all of them
            backend.script = (id, req) -> new ExecutionResult(Verdict.ACCEPTED, List.of(new ExecutionResult.TestOutcome("t", true, "PASS", null)), 1, 1, "", 1, 1, 1);
            assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).as(slug).isEqualTo("INTERNAL_ERROR");
            // ACCEPTED while one hidden test did not pass
            backend.script = (id, req) -> {
                var ok = ScriptedBackend.allPass(req);
                var tests = new ArrayList<>(ok.tests());
                tests.set(tests.size() - 1, new ExecutionResult.TestOutcome("t", false, "FAIL", null));
                return new ExecutionResult(Verdict.ACCEPTED, tests, tests.size() - 1, tests.size(), "", 1, 1, 1);
            };
            assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).as(slug).isEqualTo("INTERNAL_ERROR");
            // the visibility flags are not the ones that were sent
            backend.script = (id, req) -> {
                var tests = new ArrayList<ExecutionResult.TestOutcome>();
                for (int i = 0; i < req.tests().size(); i++) {
                    tests.add(new ExecutionResult.TestOutcome("t" + i, !req.tests().get(i).visible(), "PASS", null));
                }
                return new ExecutionResult(Verdict.ACCEPTED, tests, tests.size(), tests.size(), "", 1, 1, 1);
            };
            assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).as(slug).isEqualTo("INTERNAL_ERROR");
        }
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
        assertThat(count("select count(*) from coding_progress where user_id = ? and accepted_at is not null", student.getId())).isZero();
    }

    @Test
    void aStoredTestThatCannotBeReadAsAScriptStopsTheRunAndNeverReachesTheWorker() throws Exception {
        String slug = "circuit-breaker-state-machine";
        long id = jdbc.queryForObject("select c.id from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id join coding_problems p on p.id = v.problem_id "
                + "where p.slug = ? and c.visible = false order by c.sort_order limit 1", Long.class, slug);
        String original = jdbc.queryForObject("select input_spec from coding_test_cases where id = ?", String.class, id);
        String originalExpected = jdbc.queryForObject("select expected_spec from coding_test_cases where id = ?", String.class, id);
        try {
            List<String> corruptions = List.of(
                    "{\"script\": [\"rm -rf /\"]}",                       // an operation outside the list
                    "{\"script\": [\"new 2 100;ls\"]}",                    // characters outside the alphabet
                    "{\"script\": \"state\"}",                             // not an array
                    "{\"script\": [1, 2]}",                                // not text
                    "{\"steps\": []}",                                     // the other exercise's shape
                    "{}");
            for (String bad : corruptions) {
                backend.reset();
                jdbc.update("update coding_test_cases set input_spec = ?, expected_spec = ? where id = ?", bad, "{\"trace\": [\"-\"]}", id);
                JsonNode s = body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME).andExpect(status().isCreated()));
                assertThat(s.get("verdict").asText()).as(bad).isEqualTo("INTERNAL_ERROR");
                assertThat(backend.seen).as("a broken test must stop the run before the worker is asked").isEmpty();
                assertThat(s.toString()).doesNotContain("rm -rf").doesNotContain("ls");
            }
            // a trace of the wrong length
            backend.reset();
            jdbc.update("update coding_test_cases set input_spec = ?, expected_spec = ? where id = ?", "{\"script\": [\"new 2 100\", \"state\"]}", "{\"trace\": [\"-\"]}", id);
            assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).isEqualTo("INTERNAL_ERROR");
            assertThat(backend.seen).isEmpty();
        } finally {
            jdbc.update("update coding_test_cases set input_spec = ?, expected_spec = ? where id = ?", original, originalExpected, id);
        }
        assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void anotherUsersSubmissionToANewProblemIsInvisibleAndHistoryIsPerUser() throws Exception {
        String slug = "saga-compensation-coordinator";
        JsonNode s = body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME).andExpect(status().isCreated()));
        User other = newUser(USER_PREFIX + "other_" + System.nanoTime(), Role.STUDENT);
        getAs(other, "/api/coding/submissions/" + s.get("id").asLong()).andExpect(status().isNotFound());
        assertThat(body(getAs(other, "/api/coding/problems/" + slug + "/submissions"))).isEmpty();
        assertThat(body(getAs(student, "/api/coding/problems/" + slug + "/submissions"))).hasSize(1);
        assertThat(body(getAs(student, "/api/coding/submissions/" + s.get("id").asLong())).get("sourceCode").asText()).isEqualTo(SOME);
        // unauthenticated access is refused
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/coding/problems/" + slug)).andExpect(status().isUnauthorized());
    }

    @Test
    void thePilotIsUnchangedAndStillServedWithItsOwnHarness() throws Exception {
        JsonNode d = body(getAs(student, "/api/coding/problems/" + SLUG).andExpect(status().isOk()));
        assertThat(d.get("version").asInt()).isEqualTo(1);
        assertThat(d.get("sampleTests")).hasSize(3);
        assertThat(d.get("hiddenTestCount").asInt()).isEqualTo(9);
        assertThat(d.get("starterCode").asText()).contains("class RetryExecutor");
        // the pilot's samples keep their original single-line description
        assertThat(d.get("sampleTests").get(0).get("input").asText()).contains("maxAttempts = ");
        body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME).andExpect(status().isCreated()));
        assertThat(backend.last.problem()).isEqualTo(ExecutionRequest.Problem.RETRY_EXECUTOR);
        assertThat(backend.last.tests()).hasSize(12).noneMatch(t -> t instanceof ScriptTestSpec);
    }
}
