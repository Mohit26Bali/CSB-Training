package com.springlab.coding.execution;

import com.springlab.support.AbstractSpringBootTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The real application context, with the default configuration: it starts normally, execution is disabled, and nothing outside the
 * JVM was contacted to get there. (The acceptance run executes this test with Ubuntu WSL stopped and checks that it stays stopped.)
 */
class ExecutionDefaultsIntegrationTest extends AbstractSpringBootTest {

    @Autowired
    private ExecutionService service;
    @Autowired
    private ExecutionBackend backend;

    @Test
    void theApplicationStartsWithExecutionDisabledByDefault() {
        assertThat(backend).isInstanceOf(DisabledBackend.class);
        assertThat(service.availability().available()).isFalse();
        assertThat(service.availability().backend()).isEqualTo("disabled");
        assertThat(service.runningCount()).isZero();
        assertThat(service.inFlightCount()).isZero();
    }

    @Test
    void submittingWhileDisabledIsRefusedAndRunsNothing() {
        ExecutionRequest r = new ExecutionRequest(ExecutionRequest.Problem.RETRY_EXECUTOR, ExecutionRequest.Mode.SUBMIT,
                LearnerSources.REFERENCE.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> service.submit("someone", r)).isInstanceOf(ExecutionUnavailableException.class);
        assertThat(service.inFlightCount()).isZero();
        assertThat(service.peakRunning()).isZero();
    }
}
