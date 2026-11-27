package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.coding.execution.ExecutionRequest;
import com.springlab.coding.execution.ExecutionResult;
import com.springlab.coding.execution.ScriptTestSpec;
import com.springlab.coding.execution.Verdict;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase F6 (the easy tier) through the API with a scripted worker ANSWER: catalogue, topics, detail pages, the harness the BACKEND picks from
 * the database, hidden-test secrecy, and worker answers that disagree with the tests that were sent. The real worker is exercised in
 * {@link CodingEasyRealWorkerIntegrationTest}.
 */
class CodingEasyApiIntegrationTest extends AbstractCodingTest {

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        ScriptedBackend scriptedBackend() {
            return new ScriptedBackend();
        }
    }

    /** slug -> {harness kind, class name, topic, difficulty}. */
    static final Map<String, String[]> EASY = new LinkedHashMap<>();

    static {
        EASY.put("password-validator", new String[]{"PASSWORD_VALIDATOR", "PasswordValidator", "java-core", "EASY"});
        EASY.put("loan-eligibility", new String[]{"LOAN_ELIGIBILITY", "LoanEligibility", "java-core", "EASY"});
        EASY.put("salary-calculator", new String[]{"SALARY_CALCULATOR", "SalaryCalculator", "java-core", "EASY"});
        EASY.put("bank-account", new String[]{"BANK_ACCOUNT", "BankAccount", "java-core", "EASY"});
        EASY.put("training-registry", new String[]{"TRAINING_REGISTRY", "TrainingRegistry", "java-core", "EASY"});
        EASY.put("grade-calculator-fix", new String[]{"GRADE_CALCULATOR", "GradeCalculator", "java-core", "EASY"});
        EASY.put("request-validator", new String[]{"REQUEST_VALIDATOR", "RequestValidator", "spring-boot-basics", "EASY"});
        EASY.put("product-service-crud", new String[]{"PRODUCT_SERVICE", "ProductService", "spring-boot-basics", "MEDIUM"});
        EASY.put("exception-to-http-status", new String[]{"ERROR_MAPPER", "ErrorMapper", "spring-boot-basics", "EASY"});
        EASY.put("bean-registry-primary", new String[]{"BEAN_REGISTRY", "BeanRegistry", "spring-boot-basics", "MEDIUM"});
    }

    private static final String SOME = "public class Anything { }\n";

    @Autowired
    private ScriptedBackend backend;

    @BeforeEach
    void fresh() {
        backend.reset();
    }

    @Test
    void theNewTopicsAppearWithTheirProblemCounts() throws Exception {
        getAs(student, "/api/coding/topics").andExpect(status().isOk());
        JsonNode topics = body(getAs(student, "/api/coding/topics"));
        Map<String, Integer> counts = new LinkedHashMap<>();
        topics.forEach(t -> counts.put(t.get("slug").asText(), t.get("problemCount").asInt()));
        assertThat(counts).containsEntry("java-core", 6).containsEntry("spring-boot-basics", 4).containsEntry("microservices", 6);
    }

    @Test
    void theCatalogueFiltersByTopicAndDifficulty() throws Exception {
        JsonNode core = body(getAs(student, "/api/coding/problems?topic=java-core").andExpect(status().isOk()));
        assertThat(core).hasSize(6);
        JsonNode boot = body(getAs(student, "/api/coding/problems?topic=spring-boot-basics").andExpect(status().isOk()));
        assertThat(boot).hasSize(4);
        JsonNode easy = body(getAs(student, "/api/coding/problems?difficulty=EASY").andExpect(status().isOk()));
        List<String> easySlugs = new ArrayList<>();
        easy.forEach(n -> easySlugs.add(n.get("slug").asText()));
        assertThat(easySlugs).contains("password-validator", "loan-eligibility", "salary-calculator", "bank-account", "training-registry", "grade-calculator-fix", "request-validator", "exception-to-http-status")
                .doesNotContain("product-service-crud", "bean-registry-primary", "retry-with-bounded-backoff");
        for (JsonNode n : core) {
            assertThat(n.get("topicSlug").asText()).isEqualTo("java-core");
            assertThat(n.get("language").asText()).isEqualTo("java17");
            assertThat(n.get("version").asInt()).isEqualTo(1);
            assertThat(n.get("accepted").asBoolean()).isFalse();
        }
        assertThat(core.toString() + boot).doesNotContain("harness").doesNotContain("PASSWORD_VALIDATOR").doesNotContain("BEAN_REGISTRY");
    }

    @Test
    void eachProblemOpensWithEverythingALearnerNeeds() throws Exception {
        for (var e : EASY.entrySet()) {
            String slug = e.getKey();
            JsonNode d = body(getAs(student, "/api/coding/problems/" + slug).andExpect(status().isOk()));
            assertThat(d.get("topicSlug").asText()).as(slug).isEqualTo(e.getValue()[2]);
            assertThat(d.get("difficulty").asText()).as(slug).isEqualTo(e.getValue()[3]);
            assertThat(d.get("statement").asText().length()).as(slug).isGreaterThan(500);
            assertThat(d.get("objectives").size()).as(slug).isGreaterThanOrEqualTo(3);
            assertThat(d.get("constraints").asText()).as(slug).isNotBlank();
            assertThat(d.get("examples").size()).as(slug).isGreaterThanOrEqualTo(2);
            for (JsonNode ex : d.get("examples")) {
                assertThat(ex.get("title").asText()).isNotBlank();
                assertThat(ex.get("input").asText()).isNotBlank();
                assertThat(ex.get("output").asText()).isNotBlank();
                assertThat(ex.get("explanation").asText()).isNotBlank();
            }
            assertThat(d.get("starterCode").asText()).as(slug).contains("class " + e.getValue()[1]);
            assertThat(d.get("sampleTests")).as(slug).hasSize(3);
            for (JsonNode s : d.get("sampleTests")) {
                assertThat(s.get("input").asText()).startsWith("1. new");
                assertThat(s.get("expected").asText()).startsWith("1. ");
            }
            assertThat(d.get("hiddenTestCount").asInt()).as(slug).isGreaterThanOrEqualTo(4);
            assertThat(d.get("statement").asText()).as(slug).contains("In this exercise");
            String text = d.toString();
            assertThat(text).as(slug).doesNotContain("harness").doesNotContain("input_spec").doesNotContain("expected_spec");
            for (var row : jdbc.queryForList("select c.name from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                    + "join coding_problems p on p.id = v.problem_id where p.slug = ? and c.visible = false", slug)) {
                assertThat(text).as(slug + " hidden name").doesNotContain((String) row.get("name"));
            }
        }
    }

    @Test
    void theBackendPicksTheHarnessFromTheDatabaseWhateverTheRequestSays() throws Exception {
        for (var e : EASY.entrySet()) {
            backend.reset();
            String forged = objectMapper.writeValueAsString(Map.of("sourceCode", SOME, "harness", "RETRY_EXECUTOR", "harnessKind", "SAGA", "problem", "BULKHEAD",
                    "tests", List.of(Map.of("name", "mine", "visible", true))));
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/coding/problems/" + e.getKey() + "/submit")
                    .header("Authorization", bearer(student)).contentType("application/json").content(forged)).andExpect(status().isCreated());
            ExecutionRequest sent = backend.last;
            assertThat(sent.problem().name()).as(e.getKey()).isEqualTo(e.getValue()[0]);
            assertThat(sent.tests()).as(e.getKey()).isNotEmpty().allMatch(t -> t instanceof ScriptTestSpec);
            assertThat(sent.tests().stream().map(t -> t.name())).doesNotContain("mine");
            assertThat(sent.tests()).hasSize((int) count("select count(*) from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                    + "join coding_problems p on p.id = v.problem_id where p.slug = ?", e.getKey()));
            purge(student);
        }
    }

    @Test
    void aRunJudgesOnlyTheVisibleTestsAndASubmitJudgesAllAndRecordsProgress() throws Exception {
        for (String slug : EASY.keySet()) {
            backend.reset();
            JsonNode r = body(postSource(student, "/api/coding/problems/" + slug + "/run", SOME).andExpect(status().isOk()));
            assertThat(r.get("total").asInt()).as(slug).isEqualTo(3);
            assertThat(backend.last.tests()).hasSize(3).allMatch(t -> t.visible());
            JsonNode s = body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME).andExpect(status().isCreated()));
            assertThat(s.get("verdict").asText()).isEqualTo("ACCEPTED");
            assertThat(s.get("passed").asInt()).isEqualTo(s.get("total").asInt());
            assertThat(s.get("visibleTests")).hasSize(3);
        }
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isEqualTo(10);
    }

    @Test
    void aFailedHiddenTestNeverLeaksItsNameOrDetail() throws Exception {
        backend.script = (id, req) -> ScriptedBackend.hiddenFails(req);
        for (String slug : EASY.keySet()) {
            String text = text(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME).andExpect(status().isCreated()));
            assertThat(text).as(slug).contains("WRONG_ANSWER").doesNotContain("LEAKED-HIDDEN-DETAIL").doesNotContain("expected_spec");
            for (var row : jdbc.queryForList("select c.name from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                    + "join coding_problems p on p.id = v.problem_id where p.slug = ? and c.visible = false", slug)) {
                assertThat(text).as(slug).doesNotContain((String) row.get("name"));
            }
        }
        assertThat(count("select count(*) from coding_submission_results r join coding_submissions s on s.id = r.submission_id where s.user_id = ? and r.message is not null", student.getId())).isZero();
    }

    @Test
    void aWorkerAnswerThatDisagreesWithTheTestsSentIsNeverAnAcceptance() throws Exception {
        for (String slug : EASY.keySet()) {
            backend.script = (id, req) -> new ExecutionResult(Verdict.ACCEPTED, List.of(new ExecutionResult.TestOutcome("t", true, "PASS", null)), 1, 1, "", 1, 1, 1);
            assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).as(slug).isEqualTo("INTERNAL_ERROR");
            backend.script = (id, req) -> {
                var tests = new ArrayList<>(ScriptedBackend.allPass(req).tests());
                tests.set(tests.size() - 1, new ExecutionResult.TestOutcome("t", false, "FAIL", null));
                return new ExecutionResult(Verdict.ACCEPTED, tests, tests.size() - 1, tests.size(), "", 1, 1, 1);
            };
            assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).as(slug).isEqualTo("INTERNAL_ERROR");
        }
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
    }

    @Test
    void aStoredTestThatBreaksTheRulesStopsTheRunBeforeTheWorkerIsAsked() throws Exception {
        String slug = "request-validator";
        long id = jdbc.queryForObject("select c.id from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id join coding_problems p on p.id = v.problem_id "
                + "where p.slug = ? and c.visible = false order by c.sort_order limit 1", Long.class, slug);
        String original = jdbc.queryForObject("select input_spec from coding_test_cases where id = ?", String.class, id);
        String originalExpected = jdbc.queryForObject("select expected_spec from coding_test_cases where id = ?", String.class, id);
        try {
            for (String bad : List.of("{\"script\": [\"rm -rf /\"]}", "{\"script\": [\"new\", \"call validate a;ls b 1\"]}", "{\"script\": [\"new\", \"exec x\"]}", "{\"script\": \"new\"}", "{}")) {
                backend.reset();
                jdbc.update("update coding_test_cases set input_spec = ?, expected_spec = ? where id = ?", bad, "{\"trace\": [\"-\", \"-\"]}", id);
                assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).as(bad).isEqualTo("INTERNAL_ERROR");
                assertThat(backend.seen).as("a broken test must stop the run before the worker is asked").isEmpty();
            }
        } finally {
            jdbc.update("update coding_test_cases set input_spec = ?, expected_spec = ? where id = ?", original, originalExpected, id);
        }
        assertThat(body(postSource(student, "/api/coding/problems/" + slug + "/submit", SOME)).get("verdict").asText()).isEqualTo("ACCEPTED");
    }

    @Test
    void theEarlierProblemsAreUnchangedAndKeepTheirOwnHarness() throws Exception {
        body(postSource(student, "/api/coding/problems/" + SLUG + "/submit", SOME).andExpect(status().isCreated()));
        assertThat(backend.last.problem()).isEqualTo(ExecutionRequest.Problem.RETRY_EXECUTOR);
        assertThat(backend.last.tests()).hasSize(12);
        body(postSource(student, "/api/coding/problems/circuit-breaker-state-machine/submit", SOME).andExpect(status().isCreated()));
        assertThat(backend.last.problem()).isEqualTo(ExecutionRequest.Problem.CIRCUIT_BREAKER);
    }
}
