package com.springlab.lab.micro.run;

import java.util.EnumSet;
import java.util.Set;

/**
 * The lifecycle of a run. Only these transitions are legal; the database enforces them with a compare-and-set
 * ({@code update ... where state = :expected}), so two concurrent requests can never both move a run out of the same state.
 *
 * <pre>
 *   CREATED ──execute──► EXECUTING ──► COMPLETED ──reset──► CREATED
 *                                  └─► FAILED    ──reset──► CREATED
 * </pre>
 */
public enum RunState {
    CREATED, EXECUTING, COMPLETED, FAILED;

    public Set<RunState> allowedNext() {
        return switch (this) {
            case CREATED -> EnumSet.of(EXECUTING);
            case EXECUTING -> EnumSet.of(COMPLETED, FAILED);
            case COMPLETED, FAILED -> EnumSet.of(CREATED);
        };
    }

    public boolean canMoveTo(RunState next) {
        return allowedNext().contains(next);
    }

    public boolean isFinished() {
        return this == COMPLETED || this == FAILED;
    }
}
