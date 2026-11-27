package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ExecutionConfig.class)
            .withBean(ObjectMapper.class, ObjectMapper::new);

    @Test
    void theDefaultIsTheDisabledBackend() {
        runner.run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ExecutionBackend.class)).isInstanceOf(DisabledBackend.class);
            assertThat(ctx.getBean(ExecutionProperties.class).maxWorkers()).isEqualTo(4);
            assertThat(ctx.getBean(ExecutionProperties.class).queueCapacity()).isEqualTo(16);
            assertThat(ctx.getBean(ExecutionBackend.class).availability().available()).isFalse();
        });
    }

    @Test
    void wslMustBeRequestedExplicitlyAndWithoutItsSettingsItIsUnavailable() {
        runner.withPropertyValues("springlab.coding.execution.backend=WSL").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ExecutionBackend.class)).isInstanceOf(WslBackend.class);
            Availability a = ctx.getBean(ExecutionBackend.class).availability();
            assertThat(a.available()).isFalse();
            assertThat(a.reason()).contains("distro").contains("base-dir");
        });
    }

    @Test
    void wslWithItsSettingsIsConfiguredButStartsNothingAtStartup() {
        runner.withPropertyValues("springlab.coding.execution.backend=WSL", "springlab.coding.execution.wsl.distro=Ubuntu",
                "springlab.coding.execution.wsl.base-dir=/home/someone/springlab-judge").run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(ctx.getBean(ExecutionProperties.class).wsl().distro()).isEqualTo("Ubuntu");
            assertThat(ctx.getBean(ExecutionProperties.class).wsl().executable()).isEqualTo("wsl.exe");
            // the context was created and nothing was probed: availability() is only evaluated when asked for
        });
    }

    @Test
    void anUnknownBackendNameFailsStartupInsteadOfFallingBackToSomethingElse() {
        runner.withPropertyValues("springlab.coding.execution.backend=LOCAL").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("springlab.coding.execution.backend=IN_PROCESS").run(ctx -> assertThat(ctx).hasFailed());
        runner.withPropertyValues("springlab.coding.execution.backend=PROCESS").run(ctx -> assertThat(ctx).hasFailed());
    }

    @Test
    void limitsOutsideTheApprovedRangeFailStartup() {
        for (String bad : new String[]{"max-workers=0", "max-workers=5", "max-workers=-1", "max-workers=100", "queue-capacity=0", "queue-capacity=101",
                "hard-timeout-seconds=1", "hard-timeout-seconds=301"}) {
            runner.withPropertyValues("springlab.coding.execution." + bad).run(ctx -> assertThat(ctx).as(bad).hasFailed());
        }
        runner.withPropertyValues("springlab.coding.execution.max-workers=4", "springlab.coding.execution.queue-capacity=100",
                "springlab.coding.execution.hard-timeout-seconds=300").run(ctx -> assertThat(ctx).hasNotFailed());
    }

    @Test
    void rejectedSubmissionsMapToTheAgreedHttpStatuses() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        assertThat(status(handler, new ExecutionCapacityException())).isEqualTo(429);
        assertThat(status(handler, new ExecutionBusyException())).isEqualTo(409);
        assertThat(status(handler, new ExecutionUnavailableException("no"))).isEqualTo(503);
        assertThat(status(handler, new InvalidSourceException("bad"))).isEqualTo(400);
    }

    private static int status(GlobalExceptionHandler handler, ExecutionRejectedException e) {
        ProblemDetail p = handler.handleExecutionRejected(e);
        assertThat(p.getDetail()).isEqualTo(e.getMessage());
        return p.getStatus();
    }
}
