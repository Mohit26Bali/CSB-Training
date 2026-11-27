package com.springlab.coding.execution;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A scripted test is validated against the exercise it belongs to, and nothing outside the allowed alphabet can reach the worker's line format. */
class ScriptTestSpecTest {

    private static final ExecutionRequest.Problem CB = ExecutionRequest.Problem.CIRCUIT_BREAKER;

    private static ScriptTestSpec valid() {
        return ScriptTestSpec.of(CB, "secret-name", false, List.of("new 2 100", "state"), List.of("-", "CLOSED"));
    }

    @Test
    void aValidTestBecomesOneWireLineWithEncodedFieldsAndNoRawText() {
        String line = valid().toWireLine(4);
        String[] f = line.split(" ");
        assertThat(f).hasSize(6);
        assertThat(f[0]).isEqualTo("SCRIPT");
        assertThat(f[1]).isEqualTo("4");
        assertThat(f[2]).isEqualTo("0");
        assertThat(new String(Base64.getUrlDecoder().decode(f[3]), StandardCharsets.UTF_8)).isEqualTo("secret-name");
        assertThat(new String(Base64.getUrlDecoder().decode(f[4]), StandardCharsets.UTF_8)).isEqualTo("new 2 100\nstate");
        assertThat(new String(Base64.getUrlDecoder().decode(f[5]), StandardCharsets.UTF_8)).isEqualTo("-\nCLOSED");
        assertThat(line).doesNotContain("secret-name").doesNotContain("new 2 100");
    }

    @Test
    void anOperationOutsideTheExercisesOwnListIsRefused() {
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of("rm -rf"), List.of("-"))).isInstanceOf(IllegalArgumentException.class);
        // an operation of ANOTHER exercise is not an operation of this one
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of("relay 3"), List.of("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScriptTestSpec.of(ExecutionRequest.Problem.OUTBOX, "t", true, List.of("allow"), List.of("true"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theTraceMustHaveExactlyOneLinePerOperation() {
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of("new 2 100", "state"), List.of("-"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of("state"), List.of("-", "CLOSED"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of(), List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void charactersOutsideTheAlphabetAndOverlongLinesAreRefused() {
        for (String bad : List.of("new 2 100;ls", "new 2 100\nstate", "new $(id)", "new `id`", "new 2 100 | cat", "new \"2\"", "new 2 100\r", "new 2 100\u0000", "new 2 é")) {
            assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of(bad), List.of("-"))).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        for (String bad : List.of("a;b", "a\nb", "$(id)", "a|b", "\"q\"", "tab\there")) {
            assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of("state"), List.of(bad))).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        String tooLong = "new " + "1".repeat(200);
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of(tooLong), List.of("-"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of("state"), List.of("x".repeat(201)))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, List.of(""), List.of("-"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aScriptHasAtMost200Operations() {
        List<String> ok = IntStream.range(0, 200).mapToObj(i -> "state").collect(Collectors.toList());
        List<String> okTrace = IntStream.range(0, 200).mapToObj(i -> "CLOSED").collect(Collectors.toList());
        assertThat(ScriptTestSpec.of(CB, "t", true, ok, okTrace).script()).hasSize(200);
        List<String> more = IntStream.range(0, 201).mapToObj(i -> "state").collect(Collectors.toList());
        List<String> moreTrace = IntStream.range(0, 201).mapToObj(i -> "CLOSED").collect(Collectors.toList());
        assertThatThrownBy(() -> ScriptTestSpec.of(CB, "t", true, more, moreTrace)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theNameIsValidated() {
        for (String bad : new String[]{null, "", " ", "x".repeat(201), "line\nbreak"}) {
            assertThatThrownBy(() -> ScriptTestSpec.of(CB, bad, true, List.of("state"), List.of("CLOSED"))).as(String.valueOf(bad)).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void theRetryExerciseHasNoScriptedTests() {
        assertThatThrownBy(() -> ScriptTestSpec.of(ExecutionRequest.Problem.RETRY_EXECUTOR, "t", true, List.of("state"), List.of("CLOSED"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aRequestOnlyAcceptsTestsThatBelongToItsExercise() {
        byte[] src = "class X {}".getBytes(StandardCharsets.UTF_8);
        // a script test for the retry exercise, and a retry test for a scripted exercise, are both refused
        assertThatThrownBy(() -> new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.RUN, src, List.of(valid()))).isInstanceOf(IllegalArgumentException.class);
        TestSpec retry = new TestSpec("r", true, 3, 100, 800, List.of("R:ok"), "RETURN", "", -1, 1, List.of(), "ok");
        assertThatThrownBy(() -> new ExecutionRequest(CB, ExecutionRequest.Mode.RUN, src, List.of(retry))).isInstanceOf(IllegalArgumentException.class);
        assertThat(new ExecutionRequest(CB, ExecutionRequest.Mode.RUN, src, List.of(valid())).tests()).hasSize(1);
    }

    @Test
    void testDataNeverAppearsInToStringOfTheTestOrTheRequest() {
        assertThat(valid().toString()).isEqualTo("ScriptTestSpec[hidden]").doesNotContain("secret-name").doesNotContain("CLOSED");
        ExecutionRequest r = new ExecutionRequest(CB, ExecutionRequest.Mode.SUBMIT, "SECRET-SOURCE".getBytes(StandardCharsets.UTF_8), List.of(valid()));
        assertThat(r.toString()).doesNotContain("SECRET-SOURCE").doesNotContain("secret-name").contains("problem=CIRCUIT_BREAKER").contains("tests=1");
    }

    @Test
    void everyScriptedExerciseHasAnOperationListAndTheRetryExerciseHasNone() {
        for (ExecutionRequest.Problem p : ExecutionRequest.Problem.values()) {
            assertThat(p.scripted()).isEqualTo(p != ExecutionRequest.Problem.RETRY_EXECUTOR);
            assertThat(p.operations().isEmpty()).isEqualTo(!p.scripted());
        }
    }
    @Test
    void theEverydayExercisesAllowOnlyNewAndCallAndAtSignsInText() {
        ExecutionRequest.Problem p = ExecutionRequest.Problem.REQUEST_VALIDATOR;
        // an e-mail address is data in a script: the @ is allowed, nothing else new is
        ScriptTestSpec ok = ScriptTestSpec.of(p, "t", true, List.of("new", "call validate Mo a@b.com 25"), List.of("-", "-"));
        assertThat(ok.script()).hasSize(2);
        for (String bad : List.of("exec validate", "call validate Mo a@b.com 25;ls", "call validate $(id) a@b.com 1", "call validate Mo a!b.com 25", "call validate Mo a#b.com 25", "newer", "")) {
            assertThatThrownBy(() -> ScriptTestSpec.of(p, "t", true, List.of(bad), List.of("-"))).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        // an operation of another exercise is not an operation of this one
        assertThatThrownBy(() -> ScriptTestSpec.of(p, "t", true, List.of("relay 3"), List.of("0"))).isInstanceOf(IllegalArgumentException.class);
        for (ExecutionRequest.Problem q : ExecutionRequest.Problem.values()) {
            if (q.ordinal() >= ExecutionRequest.Problem.PASSWORD_VALIDATOR.ordinal()) {
                assertThat(q.operations()).as(q.name()).containsExactlyInAnyOrder("new", "call");
            }
        }
        // trace lines may hold an @ too
        assertThat(ScriptTestSpec.of(p, "t", true, List.of("call validate a b 1"), List.of("email:a@b.com")).trace()).containsExactly("email:a@b.com");
    }
}
