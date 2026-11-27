package com.springlab.coding;

import com.fasterxml.jackson.databind.JsonNode;
import com.springlab.coding.execution.ExecutionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase F6 with nothing scripted: every everyday problem judged by the real WSL worker in the real sandbox. A correct reference solution must be
 * ACCEPTED on every test (hidden ones included) and every wrong solution (one targeted defect each) must be rejected with WRONG_ANSWER.
 * The sources are in {@code src/test/resources/coding/easy/<HARNESS>/}; the wrong ones are found by their {@code wrong-} prefix. Skipped,
 * visibly, when the worker is not installed.
 */
class CodingEasyRealWorkerIntegrationTest extends AbstractCodingTest {

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

    private static String read(File f) throws Exception {
        try (InputStream in = new java.io.FileInputStream(f)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static File folder(String kind) throws Exception {
        URL url = CodingEasyRealWorkerIntegrationTest.class.getResource("/coding/easy/" + kind);
        assertThat(url).as("test resources for " + kind).isNotNull();
        return new File(url.toURI());
    }

    private JsonNode submit(String slug, String source) throws Exception {
        return body(postSource(student, "/api/coding/problems/" + slug + "/submit", source).andExpect(status().isCreated()));
    }

    private void assertNoHiddenData(String slug, String text) {
        for (String name : jdbc.queryForList("select c.name from coding_test_cases c join coding_problem_versions v on v.id = c.problem_version_id "
                + "join coding_problems p on p.id = v.problem_id where p.slug = ? and c.visible = false", String.class, slug)) {
            assertThat(text).as("hidden data in a learner response").doesNotContain(name);
        }
        assertThat(text).doesNotContain("expected_spec").doesNotContain("input_spec").doesNotContain("/home/").doesNotContain("jdk17").doesNotContain("bwrap");
    }

    @Test
    void everyReferenceSolutionPassesEveryTestAndIsRecorded() throws Exception {
        requireWorker();
        for (var e : CodingEasyApiIntegrationTest.EASY.entrySet()) {
            String slug = e.getKey();
            String reference = read(new File(folder(e.getValue()[0]), "reference.java"));
            JsonNode run = body(postSource(student, "/api/coding/problems/" + slug + "/run", reference).andExpect(status().isOk()));
            assertThat(run.get("verdict").asText()).as(slug + " run " + run).isEqualTo("ACCEPTED");
            assertThat(run.get("total").asInt()).isEqualTo(3);
            JsonNode s = submit(slug, reference);
            assertThat(s.get("verdict").asText()).as(slug + " submit " + s).isEqualTo("ACCEPTED");
            assertThat(s.get("passed").asInt()).isEqualTo(s.get("total").asInt()).isGreaterThanOrEqualTo(7);
            assertThat(s.get("hiddenPassed").asInt()).isEqualTo(s.get("hiddenTotal").asInt()).isGreaterThanOrEqualTo(4);
            assertNoHiddenData(slug, s.toString());
        }
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isEqualTo(10);
    }

    @Test
    void everyWrongSolutionIsRejectedWithWrongAnswer() throws Exception {
        requireWorker();
        List<String> checked = new ArrayList<>();
        for (var e : CodingEasyApiIntegrationTest.EASY.entrySet()) {
            File[] wrongs = folder(e.getValue()[0]).listFiles((d, n) -> n.startsWith("wrong-") && n.endsWith(".java"));
            assertThat(wrongs).as(e.getKey() + " has wrong solutions").isNotNull().hasSizeGreaterThanOrEqualTo(3);
            for (File w : wrongs) {
                JsonNode s = submit(e.getKey(), read(w));
                assertThat(s.get("verdict").asText()).as(e.getKey() + " / " + w.getName()).isEqualTo("WRONG_ANSWER");
                assertThat(s.get("passed").asInt()).as(w.getName()).isLessThan(s.get("total").asInt());
                assertNoHiddenData(e.getKey(), s.toString());
                checked.add(w.getName());
            }
        }
        assertThat(checked).hasSizeGreaterThanOrEqualTo(40);
        assertThat(count("select count(*) from coding_submissions where user_id = ? and verdict = 'ACCEPTED'", student.getId())).isZero();
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isZero();
    }

    @Test
    void aVisibleFailureNamesTheStepThatDiffered() throws Exception {
        requireWorker();
        // the unchanged starter of the "fix the bug" problem fails sample tests: the learner sees which step and what was expected
        String starter = read(new File(folder("GRADE_CALCULATOR"), "wrong-the-starter-unchanged.java"));
        JsonNode r = body(postSource(student, "/api/coding/problems/grade-calculator-fix/run", starter).andExpect(status().isOk()));
        assertThat(r.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        boolean explained = false;
        for (JsonNode t : r.get("tests")) {
            if (t.get("status").asText().equals("FAIL")) {
                assertThat(t.get("message").asText()).contains("step ").contains("expected").contains("got");
                explained = true;
            }
        }
        assertThat(explained).isTrue();
        assertThat(r.get("feedback").asText()).contains("sample tests fail");
    }

    @Test
    void aClassMissingTheRequiredMembersIsACompileErrorThatNamesWhatIsMissing() throws Exception {
        requireWorker();
        for (var e : CodingEasyApiIntegrationTest.EASY.entrySet()) {
            JsonNode s = submit(e.getKey(), "public class " + e.getValue()[1] + " { }\n");
            assertThat(s.get("verdict").asText()).as(e.getKey() + " " + s).isEqualTo("COMPILE_ERROR");
            assertThat(s.get("compilerOutput").asText()).as(e.getKey()).contains("must declare these public members").doesNotContain("/home/").doesNotContain("/tmp/");
        }
        assertThat(body(getAs(student, "/api/coding/progress/me")).get("problemsAccepted").asInt()).isZero();
    }

    @Test
    void aSyntaxErrorIsACompileErrorWithItsLine() throws Exception {
        requireWorker();
        for (var e : CodingEasyApiIntegrationTest.EASY.entrySet()) {
            String broken = read(new File(folder(e.getValue()[0]), "reference.java")).replaceFirst("(class\\s+" + e.getValue()[1] + "[^{]*\\{)", "$1 int broken = ;");
            JsonNode s = submit(e.getKey(), broken);
            assertThat(s.get("verdict").asText()).as(e.getKey()).isEqualTo("COMPILE_ERROR");
            assertThat(s.get("compilerOutput").asText()).contains(e.getValue()[1] + ".java:").doesNotContain("/home/");
        }
    }

    @Test
    void anExceptionThrownByTheLearnersCodeIsObservedBehaviourNotACrash() throws Exception {
        requireWorker();
        String reference = read(new File(folder("LOAN_ELIGIBILITY"), "reference.java"));
        String boom = reference.replace("if (age < 21 || age > 60) {", "if (true) { throw new IllegalStateException(\"boom\"); }\n        if (age < 21 || age > 60) {");
        JsonNode s = submit("loan-eligibility", boom);
        assertThat(s.get("verdict").asText()).isEqualTo("WRONG_ANSWER");
        assertThat(s.get("visibleTests").get(0).get("message").asText()).contains("IllegalStateException");
    }

    @Test
    void anInfiniteLoopIsTimeLimitAndNeverAccepted() throws Exception {
        requireWorker();
        String reference = read(new File(folder("ERROR_MAPPER"), "reference.java"));
        String loop = reference.replace("public int statusFor(String exceptionName) {", "public int statusFor(String exceptionName) {\n        while (exceptionName == null || exceptionName != null) { }\n        ");
        JsonNode s = submit("exception-to-http-status", loop);
        assertThat(s.get("verdict").asText()).isEqualTo("TIME_LIMIT");
        assertThat(s.get("feedback").asText()).contains("did not finish in time");
    }
}
