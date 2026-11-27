package com.springlab.coding.execution;

/** Where a submitted job is. A finished job carries its {@link Verdict} in the result. */
public enum ExecutionState {
    QUEUED,
    RUNNING,
    FINISHED,
    CANCELLED
}
