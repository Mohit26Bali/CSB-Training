package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.coding.execution.ExecutionService;
import com.springlab.coding.execution.Availability;
import com.springlab.coding.execution.Verdict;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The coding API against the real database with the real queue, service and persistence; only the worker's ANSWER is scripted. What is
 * proved here is the API contract, ownership, hidden-test secrecy, progress arithmetic and the handling of every verdict. That the
 * worker really judges code is proved separately, against the real WSL worker.
 */
@TestPropertySource(properties = {"springlab.coding.execution.max-workers=2", "springlab.coding.execution.queue-capacity=2"})
class CodingApiIntegrationTest extends AbstractCodingTest {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedBackend scriptedBackend() {
            return new ScriptedBackend();
        }
    }

    @Autowired
    private ScriptedBackend backend;
    @Autowired
    private ExecutionService executionService;

    @BeforeEach
    void freshBackend() {
        backend.reset();
    }

    @AfterEach
    void releaseGate() {
        if (backend.gate != null) {
            backend.gate.countDown();
        }
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long end = System.nanoTime() + 10_000_000_000L;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > end) {
                throw new AssertionError("condition not reached");
            }
            Thread.sleep(20);
        }
    }

    /** Names, inputs and values of the hidden tests, as stored. None of it may appear in anything a learner receives. */
    private List<String> hiddenSecrets() {
        List<String> secrets = new ArrayList<>();
        for (var row : jdbc.queryForList("select name from coding_test_cases where visible = false")) {
            secrets.add((String) row.get("name"));
        }
        secrets.addAll(List.of("2305843009213693952", "1099511627776", "unscripted", "LEAKED-HIDDEN-DETAIL"));
        return secrets;
    }

    private void assertNoHiddenData(String responseText) {
        for (String secret : hiddenSecrets()) {
            assertThat(responseText).as("hidden data in a learner response: " + secret).doesNotContain(secret);
        }
        assertThat(responseText).doesNotContain("expected_spec").doesNotContain("input_spec").doesNotContain("harness").doesNotContain("wsl").doesNotContain("bwrap")
                .doesNotContain("/home/").doesNotContain("jdk17").doesNotContain("sl-judge");
    }

    // ------------------------------------------------------------------------------------------------ the migrated pilot

    @Test
    void thePilotProblemIsSeededWithTwelveTestsOfWhichThreeAreVisible() {
        assertThat(count("select count(*) from coding_problems where slug = ?", SLUG)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select current_version from coding_problems where slug = ?", Integer.class, SLUG)).isEqualTo(1);
        assertThat(count("select count(*) from coding_problem_versions v join coding_problems p on p.id = v.problem_id where p.slug = ?", SLUG)).isEqualTo(1);
        assertThat(count("select count(*) from coding_test_cases t join coding_problem_versions v on v.id = t.problem_version_id join coding_problems p on p.id = v.problem_id where p.slug = ?", SLUG)).isEqualTo(12);
        assertThat(count("select count(*) from coding_test_cases t join coding_problem_versions v on v.id = t.problem_version_id join coding_problems p on p.id = v.problem_id where p.slug = ? and t.visible = true", SLUG)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select t.slug from coding_problems p join topics t on t.id = p.topic_id where p.slug = ?", String.class, SLUG)).isEqualTo("microservices");
    }

    // ------------------------------------------------------------------------------------------------ browse

    @Test
    void aStudentCanBrowseTopicsProblemsAndTheProblemDetail() throws Exception {
        getAs(student, "/api/coding/topics").andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug=='microservices')].problemCount").value(org.hamcrest.Matchers.hasItem(6))); // the pilot and the five F5 problems
        String pilot = "$[?(@.slug=='" + SLUG + "')]";
        getAs(student, "/api/coding/problems").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(16))
                .andExpect(jsonPath(pilot + ".difficulty").value(org.hamcrest.Matchers.hasItem("MEDIUM"))).andExpect(jsonPath(pilot + ".accepted").value(org.hamcrest.Matchers.hasItem(false)))
                .andExpect(jsonPath(pilot + ".attempts").value(org.hamcrest.Matchers.hasItem(0)));
        getAs(student, "/api/coding/problems?topic=microservices&difficulty=MEDIUM").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(count("select count(*) from coding_problems p join topics t on t.id = p.topic_id where t.slug = 'microservices' and p.difficulty = 'MEDIUM' and p.active = true")))
                .andExpect(jsonPath(pilot + ".slug").value(org.hamcrest.Matchers.hasItem(SLUG)));
        getAs(student, "/api/coding/problems?difficulty=EASY").andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(count("select count(*) from coding_problems where difficulty = 'EASY' and active = true")));
        getAs(student, "/api/coding/problems?difficulty=IMPOSSIBLE").andExpect(status().isBadRequest());
        getAs(student, "/api/coding/problems?topic=no-such-topic").andExpect(status().isBadRequest());

        JsonNode d = body(getAs(student, "/api/coding/problems/" + SLUG).andExpect(status().isOk()));
        assertThat(d.get("title").asText()).isEqualTo("Retry with bounded backoff");
        assertThat(d.get("language").asText()).isEqualTo("java17");
        assertThat(d.get("version").asInt()).isEqualTo(1);
        assertThat(d.get("topicSlug").asText()).isEqualTo("microservices");
        assertThat(d.get("statement").asText()).contains("RetryExecutor.execute").contains("Sleeper").contains("TransientException").contains("maxAttempts");
        assertThat(d.get("starterCode").asText()).contains("public final class RetryExecutor").contains("TODO");
        assertThat(d.get("objectives")).hasSize(6);
        assertThat(d.get("examples")).hasSize(3);
        assertThat(d.get("examples").get(1).get("explanation").asText()).isNotBlank();
        assertThat(d.get("constraints").asText()).isNotBlank();
        assertThat(d.get("sampleTests")).hasSize(3);
        assertThat(d.get("hiddenTestCount").asInt()).isEqualTo(9);
        assertThat(d.get("sampleTests").get(1).get("input").asText()).contains("maxAttempts = 4").contains("throws TransientException");
        assertThat(d.get("sampleTests").get(1).get("expected").asText()).contains("[100, 200]");
    }

    @Test
    void theProblemDetailAndEveryOtherResponseHoldNoHiddenTestData() throws Exception {
        String detail = text(getAs(student, "/api/coding/problems/" + SLUG).andExpect(status().isOk()));
        assertNoHiddenData(detail);
        assertNoHiddenData(text(getAs(student, "/api/coding/problems").andExpect(status().isOk())));
        assertNoHiddenData(text(getAs(student, "/api/coding/topics").andExpect(status().isOk())));
        // the detail has exactly the expected top-level fields: nothing about tests beyond the samples and a count
        assertThat(body(getAs(student, "/api/coding/problems/" + SLUG)).fieldNames()).toIterable().containsExactlyInAnyOrder("slug", "title", "difficulty", "topicSlug",
                "topicName", "language", "version", "summary", "statement", "objectives", "constraints", "examples", "starterCode", "sampleTests", "hiddenTestCount");
    }

    @Test
    void anUnknownOrInactiveProblemIs404() throws Exception {
        for (String path : new String[]{"/api/coding/problems/no-such-problem", "/api/coding/problems/no-such-problem/submissions"}) {
            getAs(student, path).andExpect(status().isNotFound());
        }
        postSource(student, "/api/coding/problems/no-such-problem/run", SOME_SOURCE).andExpect(status().isNotFound());
        postSource(student, "/api/coding/problems/no-such-problem/submit", SOME_SOURCE).andExpect(status().isNotFound());
        jdbc.update("update coding_problems set active = false where slug = ?", SLUG);
        try {
            getAs(student, "/api/coding/problems/" + SLUG).andExpect(status().isNotFound());
            postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isNotFound());
            // the inactive pilot disappears from the list; the five other problems stay
            getAs(student, "/api/coding/problems").andExpect(jsonPath("$.length()").value(15)).andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')]").isEmpty());
        } finally {
            jdbc.update("update coding_problems set active = true where slug = ?", SLUG);
        }
        assertThat(backend.seen).isEmpty();
    }

    // ------------------------------------------------------------------------------------------------ authentication and roles

    @Test
    void everyEndpointRequiresAuthentication() throws Exception {
        for (String path : new String[]{"/api/coding/topics", "/api/coding/problems", "/api/coding/problems/" + SLUG, "/api/coding/problems/" + SLUG + "/submissions",
                "/api/coding/submissions/1", "/api/coding/progress/me"}) {
            mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
        }
        for (String path : new String[]{"/api/coding/problems/" + SLUG + "/run", "/api/coding/problems/" + SLUG + "/submit"}) {
            mockMvc.perform(post(path).contentType("application/json").content("{\"sourceCode\":\"x\"}")).andExpect(status().isUnauthorized());
        }
        assertThat(backend.seen).isEmpty();
    }

    @Test
    void teachersAndAdminsMayPracticeToo() throws Exception {
        for (Role role : List.of(Role.TEACHER, Role.ADMIN)) {
            User u = newUser(USER_PREFIX + role.name().toLowerCase() + "_" + System.nanoTime(), role);
            getAs(u, "/api/coding/problems").andExpect(status().isOk());
            postSource(u, "/api/coding/problems/" + SLUG + "/run", SOME_SOURCE).andExpect(status().isOk()).andExpect(jsonPath("$.verdict").value("ACCEPTED"));
            postSource(u, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated());
            getAs(u, "/api/coding/progress/me").andExpect(status().isOk()).andExpect(jsonPath("$.problemsAccepted").value(1));
        }
    }

    // ------------------------------------------------------------------------------------------------ source validation

    @Test
    void invalidSourcesAreRefusedBeforeAnythingIsQueued() throws Exception {
        String path = "/api/coding/problems/" + SLUG + "/submit";
        mockMvc.perform(post(path).header("Authorization", bearer(student)).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearer(student)).contentType("application/json").content("")).andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearer(student)).contentType("application/json").content("{\"sourceCode\":\"\"}")).andExpect(status().isBadRequest());
        mockMvc.perform(post(path).header("Authorization", bearer(student)).contentType("application/json").content("{\"sourceCode\":null}")).andExpect(status().isBadRequest());
        // wrong fields are ignored: a learner cannot add tests, a problem key or a harness
        mockMvc.perform(post(path).header("Authorization", bearer(student)).contentType("application/json")
                .content("{\"sourceCode\":\"" + "x".repeat(70_001) + "\"}")).andExpect(status().isBadRequest());
        postSource(student, path, "x".repeat(65 * 1024)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("larger")));
        postSource(student, path, "class A {}\u0000").andExpect(status().isBadRequest());
        postSource(student, "/api/coding/problems/" + SLUG + "/run", "x".repeat(65 * 1024)).andExpect(status().isBadRequest());
        assertThat(backend.seen).isEmpty();
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isZero();
        // and the user is not locked out afterwards
        postSource(student, path, SOME_SOURCE).andExpect(status().isCreated());
    }

    @Test
    void aLearnerCannotSupplyTestsOrAProblemKey() throws Exception {
        String smuggled = "{\"sourceCode\":" + objectMapper.writeValueAsString(SOME_SOURCE)
                + ",\"tests\":[{\"name\":\"mine\",\"visible\":true}],\"problem\":\"RETRY_EXECUTOR\",\"harnessKind\":\"OTHER\",\"expected\":\"ACCEPTED\",\"mode\":\"RUN\"}";
        mockMvc.perform(post("/api/coding/problems/" + SLUG + "/submit").header("Authorization", bearer(student)).contentType("application/json").content(smuggled))
                .andExpect(status().isCreated());
        // what the worker received is exactly the 12 tests of the database, in SUBMIT mode, for the retry harness
        assertThat(backend.last.tests()).hasSize(12);
        assertThat(backend.last.tests().stream().map(t -> t.name())).doesNotContain("mine");
        assertThat(backend.last.mode().name()).isEqualTo("SUBMIT");
        assertThat(backend.last.problem().name()).isEqualTo("RETRY_EXECUTOR");
    }

    // ------------------------------------------------------------------------------------------------ run

    @Test
    void aSampleRunJudgesOnlyTheVisibleTestsAndChangesNothing() throws Exception {
        JsonNode r = body(postSource(student, "/api/coding/problems/" + SLUG + "/run", SOME_SOURCE).andExpect(status().isOk()));
        assertThat(r.get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(r.get("total").asInt()).isEqualTo(3);
        assertThat(r.get("passed").asInt()).isEqualTo(3);
        assertThat(r.get("tests")).hasSize(3);
        assertThat(r.get("tests").get(0).get("name").asText()).isEqualTo("Returns the first successful result");
        assertThat(backend.last.mode().name()).isEqualTo("RUN");
        assertThat(backend.last.tests()).hasSize(3).allMatch(t -> t.visible());
        // nothing was stored and nothing counts
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isZero();
        assertThat(count("select count(*) from coding_progress where user_id = ?", student.getId())).isZero();
        getAs(student, "/api/coding/progress/me").andExpect(jsonPath("$.totalFullSubmissions").value(0)).andExpect(jsonPath("$.problemsAttempted").value(0))
                .andExpect(jsonPath("$.problemsAccepted").value(0));
        assertNoHiddenData(r.toString());
    }

    @Test
    void aSampleRunThatFailsShowsTheVisibleMessageAndStillChangesNothing() throws Exception {
        backend.script = (id, req) -> ScriptedBackend.visibleFails(req);
        JsonNode r = body(postSource(student, "/api/coding/problems/" + SLUG + "/run", SOME_SOURCE).andExpect(status().isOk()));
        assertThat(r.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(r.get("tests").get(0).get("status").asText()).isEqualTo("FAIL");
        assertThat(r.get("tests").get(0).get("message").asText()).contains("expected waits");
        assertThat(r.get("feedback").asText()).contains("sample tests fail");
        assertThat(count("select count(*) from coding_progress where user_id = ?", student.getId())).isZero();
    }

    // ------------------------------------------------------------------------------------------------ submit and progress

    @Test
    void anAcceptedSubmissionIsStoredWithItsResultsAndCountsOnceInProgress() throws Exception {
        MvcResult res = postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Location", org.hamcrest.Matchers.startsWith("/api/coding/submissions/"))).andReturn();
        JsonNode s = objectMapper.readTree(res.getResponse().getContentAsString());
        assertThat(s.get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(s.get("passed").asInt()).isEqualTo(12);
        assertThat(s.get("total").asInt()).isEqualTo(12);
        assertThat(s.get("hiddenTotal").asInt()).isEqualTo(9);
        assertThat(s.get("hiddenPassed").asInt()).isEqualTo(9);
        assertThat(s.get("visibleTests")).hasSize(3);
        assertThat(s.get("problemVersion").asInt()).isEqualTo(1);
        assertThat(s.get("feedback").asText()).contains("All tests passed");
        long id = s.get("id").asLong();
        assertNoHiddenData(s.toString());

        assertThat(count("select count(*) from coding_submissions where id = ? and user_id = ? and verdict = 'ACCEPTED'", id, student.getId())).isEqualTo(1);
        assertThat(count("select count(*) from coding_submission_results where submission_id = ?", id)).isEqualTo(12);
        assertThat(count("select count(*) from coding_submission_results where submission_id = ? and message is not null", id)).isZero();
        assertThat(jdbc.queryForObject("select source_code from coding_submissions where id = ?", String.class, id)).isEqualTo(SOME_SOURCE);
        assertThat(jdbc.queryForObject("select source_sha256 from coding_submissions where id = ?", String.class, id)).hasSize(64);

        // progress after the first accepted submission
        JsonNode p = body(getAs(student, "/api/coding/progress/me").andExpect(status().isOk()));
        assertThat(p.get("problemsAttempted").asInt()).isEqualTo(1);
        assertThat(p.get("problemsAccepted").asInt()).isEqualTo(1);
        assertThat(p.get("totalFullSubmissions").asInt()).isEqualTo(1);
        assertThat(p.get("problems").get(0).get("latestVerdict").asText()).isEqualTo("ACCEPTED");
        assertThat(p.get("problems").get(0).get("slug").asText()).isEqualTo(SLUG);

        // a second accepted submission adds an attempt but does not accept the problem a second time
        long firstAcceptedAt = jdbc.queryForObject("select unix_timestamp(accepted_at) from coding_progress where user_id = ?", Long.class, student.getId());
        postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated());
        JsonNode p2 = body(getAs(student, "/api/coding/progress/me"));
        assertThat(p2.get("problemsAccepted").asInt()).isEqualTo(1);
        assertThat(p2.get("totalFullSubmissions").asInt()).isEqualTo(2);
        assertThat(jdbc.queryForObject("select accepted_submissions from coding_progress where user_id = ?", Integer.class, student.getId())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select first_accepted_submission_id from coding_progress where user_id = ?", Long.class, student.getId())).isEqualTo(id);
        assertThat(jdbc.queryForObject("select unix_timestamp(accepted_at) from coding_progress where user_id = ?", Long.class, student.getId())).isEqualTo(firstAcceptedAt);
        assertThat(count("select count(*) from coding_progress where user_id = ?", student.getId())).isEqualTo(1);
        getAs(student, "/api/coding/problems").andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].accepted").value(org.hamcrest.Matchers.hasItem(true)))
                .andExpect(jsonPath("$[?(@.slug=='" + SLUG + "')].attempts").value(org.hamcrest.Matchers.hasItem(2)));
    }

    @Test
    void aFailedSubmissionCountsAsAnAttemptButNeverAsAcceptedAndHiddenDetailIsDropped() throws Exception {
        backend.script = (id, req) -> ScriptedBackend.hiddenFails(req);
        JsonNode s = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated()));
        assertThat(s.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(s.get("passed").asInt()).isEqualTo(11);
        assertThat(s.get("total").asInt()).isEqualTo(12);
        assertThat(s.get("hiddenTotal").asInt()).isEqualTo(9);
        assertThat(s.get("hiddenPassed").asInt()).isEqualTo(8);
        assertThat(s.get("feedback").asText()).contains("1 hidden test(s) fail");
        assertNoHiddenData(s.toString()); // the scripted worker tried to leak a detail; it must not reach the learner
        assertThat(count("select count(*) from coding_submission_results where submission_id = ? and message is not null", s.get("id").asLong())).isZero();
        JsonNode p = body(getAs(student, "/api/coding/progress/me"));
        assertThat(p.get("problemsAttempted").asInt()).isEqualTo(1);
        assertThat(p.get("problemsAccepted").asInt()).isZero();
        assertThat(p.get("totalFullSubmissions").asInt()).isEqualTo(1);
        assertThat(p.get("problems").get(0).get("latestVerdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(p.get("problems").get(0).get("accepted").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select accepted_at from coding_progress where user_id = ?", java.sql.Timestamp.class, student.getId())).isNull();
    }

    @Test
    void aVisibleFailureExplainsItselfInTheStoredResultAndTheLaterRead() throws Exception {
        backend.script = (id, req) -> ScriptedBackend.visibleFails(req);
        JsonNode s = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated()));
        assertThat(s.get("visibleTests").get(0).get("message").asText()).contains("expected waits");
        JsonNode again = body(getAs(student, "/api/coding/submissions/" + s.get("id").asLong()).andExpect(status().isOk()));
        assertThat(again.get("visibleTests").get(0).get("message").asText()).contains("expected waits");
        assertThat(again.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(again.get("sourceCode").asText()).isEqualTo(SOME_SOURCE);
        assertThat(again.get("hiddenTotal").asInt()).isEqualTo(9);
        assertNoHiddenData(again.toString());
    }

    @Test
    void everyKindOfNonAcceptedOutcomeIsStoredWithItsOwnVerdictAndNeverAccepted() throws Exception {
        record Case(String name, java.util.function.Function<com.springlab.coding.execution.ExecutionRequest, com.springlab.coding.execution.ExecutionResult> answer, String verdict, boolean attempt) {
        }
        List<Case> cases = List.of(
                new Case("compile error", r -> ScriptedBackend.notRun(r, Verdict.COMPILE_ERROR, "RetryExecutor.java:7: error: ';' expected\n"), "COMPILE_ERROR", true),
                new Case("time limit", r -> ScriptedBackend.notRun(r, Verdict.TIME_LIMIT, ""), "TIME_LIMIT", true),
                new Case("runtime error", r -> ScriptedBackend.notRun(r, Verdict.RUNTIME_ERROR, ""), "RUNTIME_ERROR", true),
                new Case("memory limit", r -> ScriptedBackend.notRun(r, Verdict.MEMORY_LIMIT, ""), "MEMORY_LIMIT", true),
                new Case("output limit", r -> ScriptedBackend.notRun(r, Verdict.OUTPUT_LIMIT, ""), "OUTPUT_LIMIT", true),
                new Case("worker timeout without any test result", r -> com.springlab.coding.execution.ExecutionResult.of(Verdict.TIME_LIMIT, 60_000), "TIME_LIMIT", true),
                new Case("internal error", r -> com.springlab.coding.execution.ExecutionResult.of(Verdict.INTERNAL_ERROR), "INTERNAL_ERROR", false),
                new Case("worker said INVALID_SUBMISSION", r -> com.springlab.coding.execution.ExecutionResult.of(Verdict.INVALID_SUBMISSION), "INTERNAL_ERROR", false));
        int expectedAttempts = 0;
        for (Case c : cases) {
            backend.script = (id, req) -> c.answer().apply(req);
            JsonNode s = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated()));
            assertThat(s.get("verdict").asText()).as(c.name()).isEqualTo(c.verdict());
            assertThat(s.get("passed").asInt()).as(c.name()).isZero();
            assertThat(s.get("verdict").asText()).isNotEqualTo("ACCEPTED");
            assertThat(s.get("feedback").asText()).as(c.name()).isNotBlank();
            if (c.verdict().equals("COMPILE_ERROR")) {
                assertThat(s.get("compilerOutput").asText()).contains("RetryExecutor.java:7");
            }
            if (c.attempt()) {
                expectedAttempts++;
            }
            assertThat(count("select count(*) from coding_submissions where id = ? and verdict = ?", s.get("id").asLong(), c.verdict())).as(c.name()).isEqualTo(1);
        }
        JsonNode p = body(getAs(student, "/api/coding/progress/me"));
        assertThat(p.get("problemsAccepted").asInt()).isZero();
        assertThat(p.get("totalFullSubmissions").asInt()).as("platform failures are stored but are not attempts").isEqualTo(expectedAttempts);
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isEqualTo(cases.size());
    }

    @Test
    void anAnswerThatDoesNotMatchTheTestsThatWereSentIsNeverAnAcceptance() throws Exception {
        // ACCEPTED with one test instead of twelve
        backend.script = (id, req) -> new com.springlab.coding.execution.ExecutionResult(Verdict.ACCEPTED,
                List.of(new com.springlab.coding.execution.ExecutionResult.TestOutcome("t", true, "PASS", null)), 1, 1, "", 1, 1, 1);
        assertThat(body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("verdict").asText()).isEqualTo("INTERNAL_ERROR");
        // ACCEPTED while one hidden test did not pass
        backend.script = (id, req) -> {
            var ok = ScriptedBackend.allPass(req);
            var tests = new ArrayList<>(ok.tests());
            tests.set(5, new com.springlab.coding.execution.ExecutionResult.TestOutcome("t5", false, "FAIL", null));
            return new com.springlab.coding.execution.ExecutionResult(Verdict.ACCEPTED, tests, 11, 12, "", 1, 1, 1);
        };
        assertThat(body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("verdict").asText()).isEqualTo("INTERNAL_ERROR");
        // visibility flags swapped: the worker did not judge the tests that were sent
        backend.script = (id, req) -> {
            var tests = new ArrayList<com.springlab.coding.execution.ExecutionResult.TestOutcome>();
            for (int i = 0; i < req.tests().size(); i++) {
                tests.add(new com.springlab.coding.execution.ExecutionResult.TestOutcome("t" + i, !req.tests().get(i).visible(), "PASS", null));
            }
            return new com.springlab.coding.execution.ExecutionResult(Verdict.ACCEPTED, tests, 12, 12, "", 1, 1, 1);
        };
        assertThat(body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("verdict").asText()).isEqualTo("INTERNAL_ERROR");
        // WRONG_ANSWER although everything passed
        backend.script = (id, req) -> {
            var ok = ScriptedBackend.allPass(req);
            return new com.springlab.coding.execution.ExecutionResult(Verdict.WRONG_ANSWER, ok.tests(), 12, 12, "", 1, 1, 1);
        };
        assertThat(body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("verdict").asText()).isEqualTo("INTERNAL_ERROR");
        // a backend that throws
        backend.script = (id, req) -> {
            throw new IllegalStateException("worker exploded with LEAKED-HIDDEN-DETAIL");
        };
        String crashed = text(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated()));
        assertThat(objectMapper.readTree(crashed).get("verdict").asText()).isEqualTo("INTERNAL_ERROR");
        assertThat(crashed).doesNotContain("exploded");
        assertThat(count("select count(*) from coding_progress where user_id = ?", student.getId())).as("none of these is an attempt").isZero();
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
    }

    // ------------------------------------------------------------------------------------------------ ownership

    @Test
    void anotherUsersSubmissionBehavesExactlyLikeAnUnknownOne() throws Exception {
        long id = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("id").asLong();
        User other = newUser(USER_PREFIX + "other_" + System.nanoTime(), Role.STUDENT);
        String foreign = text(getAs(other, "/api/coding/submissions/" + id).andExpect(status().isNotFound()));
        String unknown = text(getAs(other, "/api/coding/submissions/9999999").andExpect(status().isNotFound()));
        assertThat(objectMapper.readTree(foreign).get("detail")).isEqualTo(objectMapper.readTree(unknown).get("detail"));
        assertThat(objectMapper.readTree(foreign).get("title")).isEqualTo(objectMapper.readTree(unknown).get("title"));
        assertThat(foreign).doesNotContain("RetryExecutor");
        getAs(student, "/api/coding/submissions/" + id).andExpect(status().isOk());
        // history and progress are the caller's own
        getAs(other, "/api/coding/problems/" + SLUG + "/submissions").andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        getAs(other, "/api/coding/progress/me").andExpect(jsonPath("$.totalFullSubmissions").value(0)).andExpect(jsonPath("$.problems.length()").value(0));
        getAs(student, "/api/coding/problems/" + SLUG + "/submissions").andExpect(jsonPath("$.length()").value(1));
        // not even an admin reads someone else's source through this API
        User admin = newUser(USER_PREFIX + "admin_" + System.nanoTime(), Role.ADMIN);
        getAs(admin, "/api/coding/submissions/" + id).andExpect(status().isNotFound());
        // a non-numeric id is not found either way
        getAs(student, "/api/coding/submissions/abc").andExpect(status().is4xxClientError());
    }

    @Test
    void historyListsTheCallersSubmissionsNewestFirst() throws Exception {
        backend.script = (id, req) -> ScriptedBackend.hiddenFails(req);
        long first = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("id").asLong();
        backend.script = (id, req) -> ScriptedBackend.allPass(req);
        long second = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("id").asLong();
        JsonNode h = body(getAs(student, "/api/coding/problems/" + SLUG + "/submissions").andExpect(status().isOk()));
        assertThat(h).hasSize(2);
        assertThat(h.get(0).get("id").asLong()).isEqualTo(second);
        assertThat(h.get(0).get("verdict").asText()).isEqualTo("ACCEPTED");
        assertThat(h.get(1).get("id").asLong()).isEqualTo(first);
        assertThat(h.get(1).get("problemVersion").asInt()).isEqualTo(1);
        assertThat(h.get(0).has("sourceCode")).as("the list does not carry source").isFalse();
    }

    // ------------------------------------------------------------------------------------------------ versions

    @Test
    void changingTheProblemAddsAVersionAndNeverRewritesAnEarlierSubmission() throws Exception {
        long old = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE)).get("id").asLong();
        JsonNode oldBefore = body(getAs(student, "/api/coding/submissions/" + old));
        long problemId = jdbc.queryForObject("select id from coding_problems where slug = ?", Long.class, SLUG);
        long v1 = jdbc.queryForObject("select id from coding_problem_versions where problem_id = ? and version = 1", Long.class, problemId);
        jdbc.update("insert into coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at) "
                + "select problem_id, 2, summary, 'THE SECOND VERSION OF THE STATEMENT', objectives_json, constraints_md, examples_json, starter_code, now(6) "
                + "from coding_problem_versions where id = ?", v1);
        long v2 = jdbc.queryForObject("select id from coding_problem_versions where problem_id = ? and version = 2", Long.class, problemId);
        jdbc.update("insert into coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) "
                + "select ?, sort_order, name, concept, visible, input_spec, expected_spec from coding_test_cases where problem_version_id = ? and sort_order < 6", v2, v1);
        jdbc.update("update coding_problems set current_version = 2 where id = ?", problemId);
        try {
            JsonNode detail = body(getAs(student, "/api/coding/problems/" + SLUG));
            assertThat(detail.get("version").asInt()).isEqualTo(2);
            assertThat(detail.get("statement").asText()).isEqualTo("THE SECOND VERSION OF THE STATEMENT");
            assertThat(detail.get("hiddenTestCount").asInt()).isEqualTo(3);

            JsonNode fresh = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated()));
            assertThat(fresh.get("problemVersion").asInt()).isEqualTo(2);
            assertThat(fresh.get("total").asInt()).isEqualTo(6);
            assertThat(backend.last.tests()).hasSize(6);

            // the earlier submission still reads exactly as it did: its version, its counts, its per-test results
            JsonNode oldAfter = body(getAs(student, "/api/coding/submissions/" + old));
            assertThat(oldAfter.get("problemVersion").asInt()).isEqualTo(1);
            assertThat(oldAfter.get("total").asInt()).isEqualTo(12);
            assertThat(oldAfter.get("passed").asInt()).isEqualTo(12);
            assertThat(oldAfter.get("visibleTests")).isEqualTo(oldBefore.get("visibleTests"));
            assertThat(oldAfter.get("hiddenTotal").asInt()).isEqualTo(9);
            assertThat(count("select count(*) from coding_submission_results where submission_id = ?", old)).isEqualTo(12);
            getAs(student, "/api/coding/problems/" + SLUG + "/submissions").andExpect(jsonPath("$[0].problemVersion").value(2)).andExpect(jsonPath("$[1].problemVersion").value(1));
        } finally {
            purge(student);
            jdbc.update("update coding_problems set current_version = 1 where id = ?", problemId);
            jdbc.update("delete from coding_test_cases where problem_version_id = ?", v2);
            jdbc.update("delete from coding_problem_versions where id = ?", v2);
        }
    }

    // ------------------------------------------------------------------------------------------------ execution admission

    @Test
    void whenNoBackendIsAvailableTheAnswerIs503AndNothingIsStored() throws Exception {
        backend.availability = Availability.down("scripted", "wsl is stopped");
        for (String action : new String[]{"run", "submit"}) {
            String t = text(postSource(student, "/api/coding/problems/" + SLUG + "/" + action, SOME_SOURCE).andExpect(status().isServiceUnavailable()));
            assertThat(t).doesNotContain("wsl").doesNotContain("stopped");
        }
        assertThat(backend.seen).isEmpty();
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isZero();
    }

    @Test
    void aSecondSubmissionFromTheSameUserWhileOneIsRunningIs409AndOtherUsersAreUnaffected() throws Exception {
        backend.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(4);
        Future<Integer> first = pool.submit(() -> postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andReturn().getResponse().getStatus());
        await(() -> backend.seen.size() == 1);
        postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isConflict());
        postSource(student, "/api/coding/problems/" + SLUG + "/run", SOME_SOURCE).andExpect(status().isConflict()); // a run takes the same slot
        User other = newUser(USER_PREFIX + "second_" + System.nanoTime(), Role.STUDENT);
        Future<Integer> second = pool.submit(() -> postSource(other, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andReturn().getResponse().getStatus());
        await(() -> backend.seen.size() == 2);
        backend.gate.countDown();
        assertThat(first.get()).isEqualTo(201);
        assertThat(second.get()).isEqualTo(201);
        pool.shutdown();
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isEqualTo(1);
        postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isCreated()); // the slot is free again
    }

    @Test
    void whenWorkersAndQueueAreFullTheNextSubmissionIs429() throws Exception {
        backend.gate = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        List<Future<Integer>> accepted = new ArrayList<>();
        List<User> others = new ArrayList<>();
        for (int i = 0; i < 4; i++) { // 2 workers + 2 queued
            User u = newUser(USER_PREFIX + "load" + i + "_" + System.nanoTime(), Role.STUDENT);
            others.add(u);
            accepted.add(pool.submit(() -> postSource(u, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andReturn().getResponse().getStatus()));
        }
        await(() -> executionService.inFlightCount() == 4);
        String overflow = text(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andExpect(status().isTooManyRequests()));
        assertThat(overflow).contains("busy");
        backend.gate.countDown();
        for (Future<Integer> f : accepted) {
            assertThat(f.get()).isEqualTo(201);
        }
        pool.shutdown();
        assertThat(count("select count(*) from coding_submissions where user_id = ?", student.getId())).isZero();
        assertThat(backend.seen).hasSize(4);
    }

    @Test
    void aCancelledSubmissionIsStoredAsCancelledNeverAcceptedAndIsNotAnAttempt() throws Exception {
        backend.gate = new CountDownLatch(1);
        backend.script = (id, req) -> ScriptedBackend.allPass(req); // even a worker that would have said ACCEPTED
        ExecutorService pool = Executors.newSingleThreadExecutor();
        Future<MvcResult> pending = pool.submit(() -> postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME_SOURCE).andReturn());
        await(() -> backend.seen.size() == 1);
        String jobId = backend.order.get(0);
        assertThat(executionService.cancel(student.getUsername(), jobId)).isPresent();
        JsonNode s = objectMapper.readTree(pending.get().getResponse().getContentAsString());
        pool.shutdown();
        assertThat(s.get("verdict").asText()).isEqualTo("CANCELLED");
        assertThat(s.get("passed").asInt()).isZero();
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
        assertThat(count("select count(*) from coding_progress where user_id = ?", student.getId())).isZero();
        getAs(student, "/api/coding/progress/me").andExpect(jsonPath("$.problemsAccepted").value(0)).andExpect(jsonPath("$.totalFullSubmissions").value(0));
    }

    @Test
    void sourceCodeAndTestDataNeverReachTheLog() throws Exception {
        ch.qos.logback.classic.Logger root = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender = new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        root.addAppender(appender);
        try {
            String marker = "UNIQUE-LEARNER-SOURCE-MARKER-5b1e";
            String source = SOME_SOURCE.replace("return a.call();", "return a.call(); /* " + marker + " */");
            backend.script = (id, req) -> ScriptedBackend.hiddenFails(req);
            postSource(student, "/api/coding/problems/" + SLUG + "/submit", source).andExpect(status().isCreated());
            backend.script = (id, req) -> {
                throw new IllegalStateException("boom");
            };
            postSource(student, "/api/coding/problems/" + SLUG + "/submit", source).andExpect(status().isCreated());
            List<String> logged = appender.list.stream().map(e -> e.getFormattedMessage() + " " + (e.getThrowableProxy() == null ? "" : e.getThrowableProxy().getMessage())).toList();
            assertThat(logged).isNotEmpty();
            assertThat(logged).noneMatch(l -> l.contains(marker));
            for (String secret : hiddenSecrets()) {
                assertThat(logged).as(secret).noneMatch(l -> l.contains(secret));
            }
        } finally {
            root.detachAppender(appender);
        }
    }

    @Test
    void sourceWithMultibyteCharactersRoundTripsExactly() throws Exception {
        String source = SOME_SOURCE + "// délai → π 🚀\n";
        long id = body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", source).andExpect(status().isCreated())).get("id").asLong();
        assertThat(body(getAs(student, "/api/coding/submissions/" + id)).get("sourceCode").asText()).isEqualTo(source);
        assertThat(new String(backend.last.source(), StandardCharsets.UTF_8)).isEqualTo(source);
    }
}
