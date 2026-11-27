package com.springlab.coding.execution;

import java.time.Instant;

/**
 * What a caller may know about its own job. {@code result} is null until the job finishes; a cancelled job has the verdict
 * CANCELLED and nothing else the sandbox may have produced.
 */
public record ExecutionStatus(String jobId, ExecutionState state, ExecutionResult result, Instant submittedAt) {
}
