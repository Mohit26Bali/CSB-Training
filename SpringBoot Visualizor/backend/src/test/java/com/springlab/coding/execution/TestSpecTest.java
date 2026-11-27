package com.springlab.coding.execution;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSpecTest {

    private static TestSpec valid() {
        return new TestSpec("Retries transient failures", true, 4, 100, 800, List.of("T:a", "T:b", "R:ok"), "RETURN", "", -1, 3, List.of(100L, 200L), "ok");
    }

    @Test
    void theWireLineHasTheFormatTheWorkerParses() {
        String line = valid().toWireLine(7);
        String[] f = line.split(" ");
        assertThat(f).hasSize(14);
        assertThat(f[0]).isEqualTo("TEST");
        assertThat(f[1]).isEqualTo("7");
        assertThat(f[2]).isEqualTo("1");
        assertThat(new String(Base64.getUrlDecoder().decode(f[3]))).isEqualTo("Retries transient failures");
        assertThat(f[4]).isEqualTo("4");
        assertThat(f[5]).isEqualTo("100");
        assertThat(f[6]).isEqualTo("800");
        assertThat(f[7]).isEqualTo("T:a,T:b,R:ok");
        assertThat(f[8]).isEqualTo("RETURN");
        assertThat(f[9]).isEqualTo("-");              // empty exception class
        assertThat(f[10]).isEqualTo("-1");
        assertThat(f[11]).isEqualTo("3");
        assertThat(f[12]).isEqualTo("100,200");
        assertThat(new String(Base64.getUrlDecoder().decode(f[13]))).isEqualTo("ok");
    }

    @Test
    void emptyListsAndValuesUseTheDashPlaceholder() {
        TestSpec t = new TestSpec("t", false, 0, 0, 0, List.of(), "THROW", "java.lang.IllegalArgumentException", -1, 0, List.of(), "");
        String[] f = t.toWireLine(0).split(" ");
        assertThat(f[2]).isEqualTo("0");
        assertThat(f[7]).isEqualTo("-");
        assertThat(f[12]).isEqualTo("-");
        assertThat(f[13]).isEqualTo("-");
        assertThat(new String(Base64.getUrlDecoder().decode(f[9]))).isEqualTo("java.lang.IllegalArgumentException");
    }

    @Test
    void anythingThatCouldBeMisreadByTheLineFormatIsRefused() {
        assertThatThrownBy(() -> new TestSpec("", true, 1, 1, 1, List.of(), "RETURN", "", -1, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("x".repeat(201), true, 1, 1, 1, List.of(), "RETURN", "", -1, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("bad\nname", true, 1, 1, 1, List.of(), "RETURN", "", -1, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("t", true, 1, -1, 1, List.of(), "RETURN", "", -1, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        for (String step : new String[]{"X:a", "R:", "R:has space", "R:a,b", "RR:a", "R:" + "x".repeat(21), "T:a\nTEST", "R:émoji"}) {
            assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(step), "RETURN", "", -1, 1, List.of(), "")).as(step).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(), "MAYBE", "", -1, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(), "THROW", "java.lang.Runtime", -1, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(), "RETURN", "", -1, 1, List.of(), "has space")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(), "RETURN", "", -2, 1, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(), "RETURN", "", -1, 1001, List.of(), "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TestSpec("t", true, 1, 1, 1, List.of(), "RETURN", "", -1, 1, List.of(-5L), "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aSpecNeverShowsItsDataInToString() {
        assertThat(valid().toString()).isEqualTo("TestSpec[visible]").doesNotContain("ok").doesNotContain("100");
        TestSpec hidden = new TestSpec("secret-name", false, 4, 100, 800, List.of("T:a"), "THROW", "TransientException", 0, 1, List.of(), "");
        assertThat(hidden.toString()).isEqualTo("TestSpec[hidden]").doesNotContain("secret-name");
    }

    @Test
    void aRequestShowsNeitherItsSourceNorItsTests() {
        ExecutionRequest r = new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT, "SECRET-SOURCE".getBytes(), List.of(valid()));
        assertThat(r.toString()).doesNotContain("SECRET-SOURCE").doesNotContain("Retries transient").contains("tests=1");
        assertThat(r.tests()).hasSize(1);
        @SuppressWarnings("unchecked")
        List<WorkerTest> view = (List<WorkerTest>) r.tests(); // the list is typed "? extends WorkerTest" now; it must still be unmodifiable
        assertThatThrownBy(() -> view.add(valid())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void aRequestWithMoreThanFiftyTestsIsRefused() {
        List<TestSpec> many = java.util.Collections.nCopies(51, valid());
        assertThatThrownBy(() -> new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.RUN, new byte[]{1}, many)).isInstanceOf(IllegalArgumentException.class);
    }
}
