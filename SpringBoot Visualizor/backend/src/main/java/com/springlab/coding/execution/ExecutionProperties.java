package com.springlab.coding.execution;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration of learner code execution. The default is DISABLED: nothing is started and nothing outside the JVM is contacted.
 * A real backend must be chosen explicitly:
 *
 * <pre>
 * springlab.coding.execution.backend=WSL
 * springlab.coding.execution.wsl.distro=Ubuntu
 * springlab.coding.execution.wsl.base-dir=/home/someone/springlab-judge
 * </pre>
 *
 * Limits are validated when the application starts, so a mistake fails fast instead of silently widening a limit. There is no
 * setting that turns on in-process execution, because there is no such code.
 */
@ConfigurationProperties(prefix = "springlab.coding.execution")
public record ExecutionProperties(
        @DefaultValue("DISABLED") Backend backend,
        @DefaultValue("4") int maxWorkers,
        @DefaultValue("16") int queueCapacity,
        @DefaultValue("60") int hardTimeoutSeconds,
        @DefaultValue Wsl wsl) {

    public enum Backend {
        DISABLED, WSL
    }

    /** Where the WSL worker lives. All values are checked against a strict pattern before they are ever put in a command line. */
    public record Wsl(@DefaultValue("wsl.exe") String executable, String distro, String baseDir) {
    }

    public ExecutionProperties {
        if (maxWorkers < 1 || maxWorkers > 4) {
            throw new IllegalArgumentException("springlab.coding.execution.max-workers must be between 1 and 4");
        }
        if (queueCapacity < 1 || queueCapacity > 100) {
            throw new IllegalArgumentException("springlab.coding.execution.queue-capacity must be between 1 and 100");
        }
        if (hardTimeoutSeconds < 5 || hardTimeoutSeconds > 300) {
            throw new IllegalArgumentException("springlab.coding.execution.hard-timeout-seconds must be between 5 and 300");
        }
    }
}
