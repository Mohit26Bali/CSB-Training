package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerResponseParserTest {

    private final WorkerResponseParser parser = new WorkerResponseParser(new ObjectMapper());

    private static String test(String name, boolean visible, String status, String message) {
        return "{\"name\":\"" + name + "\",\"visible\":" + visible + ",\"status\":\"" + status + "\",\"message\":" + (message == null ? "null" : "\"" + message + "\"") + "}";
    }

    private static String json(String verdict, int passed, int total, String tests) {
        return "{\"verdict\":\"" + verdict + "\",\"passed\":" + passed + ",\"total\":" + total + ",\"wallMillis\":1800,\"peakKb\":79000,\"cpuMillis\":1700,"
                + "\"compilerOutput\":\"\",\"tests\":[" + tests + "]}";
    }

    @Test
    void aValidAcceptedAnswerIsParsed() throws Exception {
        ExecutionResult r = parser.parse(json("ACCEPTED", 2, 2, test("a", true, "PASS", null) + "," + test("b", false, "PASS", null)));
        assertThat(r.verdict()).isEqualTo(Verdict.ACCEPTED);
        assertThat(r.passed()).isEqualTo(2);
        assertThat(r.tests()).hasSize(2);
        assertThat(r.wallMillis()).isEqualTo(1800);
        assertThat(r.peakKb()).isEqualTo(79000);
    }

    @Test
    void aVisibleFailureKeepsItsMessageButAHiddenOneNeverDoes() throws Exception {
        ExecutionResult r = parser.parse(json("WRONG_ANSWER", 0, 2, test("visible", true, "FAIL", "expected waits [100] but got []")
                + "," + test("hidden", false, "FAIL", "expected exactly 3 but got 4")));
        assertThat(r.tests().get(0).message()).isEqualTo("expected waits [100] but got []");
        assertThat(r.tests().get(1).message()).isNull();
        assertThat(r.toString()).doesNotContain("exactly 3");
    }

    @Test
    void everyNonAcceptedWorkerVerdictIsAccepted() throws Exception {
        for (Verdict v : new Verdict[]{Verdict.COMPILE_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT, Verdict.RUNTIME_ERROR, Verdict.INVALID_SUBMISSION}) {
            assertThat(parser.parse(json(v.name(), 0, 1, test("t", true, "NOT_RUN", null))).verdict()).isEqualTo(v);
        }
    }

    @Test
    void aCompileErrorCarriesItsDiagnostics() throws Exception {
        ExecutionResult r = parser.parse("{\"verdict\":\"COMPILE_ERROR\",\"passed\":0,\"total\":0,\"wallMillis\":900,\"peakKb\":-1,\"cpuMillis\":-1,"
                + "\"compilerOutput\":\"RetryExecutor.java:7: error: ';' expected\\n\",\"tests\":[]}");
        assertThat(r.verdict()).isEqualTo(Verdict.COMPILE_ERROR);
        assertThat(r.compilerOutput()).contains("RetryExecutor.java:7");
    }

    @Test
    void controlCharactersAreRemovedFromTextThatReachesALearner() throws Exception {
        ExecutionResult r = parser.parse(json("WRONG_ANSWER", 0, 1, test("na\\u0007me", true, "FAIL", "bell\\u0007 and escape\\u001b[31m")));
        assertThat(r.tests().get(0).name()).isEqualTo("name");
        assertThat(r.tests().get(0).message()).doesNotContain("\u0007").doesNotContain("\u001b");
    }

    @Test
    void anAnswerThatCouldBeMistakenForAPassIsMalformed() {
        // ACCEPTED while a test did not pass
        assertMalformed(json("ACCEPTED", 1, 2, test("a", true, "PASS", null) + "," + test("b", false, "FAIL", null)));
        // ACCEPTED with nothing run
        assertMalformed(json("ACCEPTED", 0, 0, ""));
        // the counters disagree with the statuses
        assertMalformed(json("WRONG_ANSWER", 2, 2, test("a", true, "FAIL", null) + "," + test("b", true, "FAIL", null)));
        assertMalformed(json("ACCEPTED", 2, 2, test("a", true, "PASS", null) + "," + test("b", true, "FAIL", null)));
        // wrong answer although everything passed
        assertMalformed(json("WRONG_ANSWER", 1, 1, test("a", true, "PASS", null)));
    }

    @Test
    void structuralProblemsAreMalformed() {
        assertMalformed("");
        assertMalformed("not json at all");
        assertMalformed("[]");
        assertMalformed("{}");
        assertMalformed("{\"verdict\":\"ACCEPTED\"");                       // truncated
        assertMalformed(json("MAYBE", 0, 0, ""));                           // unknown verdict
        assertMalformed(json("CANCELLED", 0, 0, ""));                       // not something a worker may say
        assertMalformed(json("UNAVAILABLE", 0, 0, ""));
        assertMalformed(json("ACCEPTED", 5, 2, test("a", true, "PASS", null)));          // passed above total
        assertMalformed(json("WRONG_ANSWER", 0, 3, test("a", true, "FAIL", null)));      // tests do not match total
        assertMalformed(json("WRONG_ANSWER", 0, 1, test("a", true, "SKIPPED", null)));   // unknown status
        assertMalformed(json("WRONG_ANSWER", -1, 1, test("a", true, "FAIL", null)));
        assertMalformed(json("WRONG_ANSWER", 0, 51, ""));
        assertMalformed(json("WRONG_ANSWER", 0, 1, "{\"name\":\"a\",\"visible\":\"yes\",\"status\":\"FAIL\",\"message\":null}"));
        assertMalformed(json("WRONG_ANSWER", 0, 1, "\"just a string\""));
        assertMalformed(json("WRONG_ANSWER", 0, 1, test("x".repeat(300), true, "FAIL", null)));
        assertMalformed(json("WRONG_ANSWER", 0, 1, test("a", true, "FAIL", "m".repeat(600))));
        assertMalformed("{\"verdict\":\"WRONG_ANSWER\",\"passed\":0.5,\"total\":1,\"wallMillis\":1,\"peakKb\":1,\"cpuMillis\":1,\"tests\":[" + test("a", true, "FAIL", null) + "]}");
        assertMalformed("{\"verdict\":\"TIME_LIMIT\",\"passed\":0,\"total\":0,\"wallMillis\":\"soon\",\"peakKb\":1,\"cpuMillis\":1,\"tests\":[]}");
    }

    @Test
    void extraFieldsAreIgnoredSoALearnerOutputFieldCanNeverSurface() throws Exception {
        ExecutionResult r = parser.parse("{\"verdict\":\"RUNTIME_ERROR\",\"passed\":0,\"total\":0,\"wallMillis\":10,\"peakKb\":-1,\"cpuMillis\":-1,\"compilerOutput\":\"\","
                + "\"tests\":[],\"learnerOutput\":\"SECRET-LEARNER-OUTPUT\",\"exitCode\":1}");
        assertThat(r.toString()).doesNotContain("SECRET-LEARNER-OUTPUT");
    }

    private void assertMalformed(String json) {
        assertThatThrownBy(() -> parser.parse(json)).as(json).isInstanceOf(WorkerResponseParser.MalformedResponseException.class);
    }
}
