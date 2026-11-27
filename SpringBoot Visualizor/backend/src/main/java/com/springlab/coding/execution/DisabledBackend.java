package com.springlab.coding.execution;

import java.time.Duration;

/** The default. It runs nothing and starts no process, so a stopped WSL, a missing JDK or a missing worker cannot affect startup. */
public final class DisabledBackend implements ExecutionBackend {

    public static final String NAME = "disabled";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public Availability availability() {
        return Availability.down(NAME, "Code execution is not enabled on this server");
    }

    @Override
    public ExecutionResult execute(String jobId, ExecutionRequest request, Duration hardTimeout) {
        throw new ExecutionUnavailableException("Code execution is not enabled on this server");
    }

    @Override
    public void cancel(String jobId) {
        // nothing was ever started
    }
}
