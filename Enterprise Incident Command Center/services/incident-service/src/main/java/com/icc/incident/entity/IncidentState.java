package com.icc.incident.entity;

/**
 * The six states of ServiceNow (D32). NEW to IN_PROGRESS (accept), then either RESOLVED (needs a resolution)
 * and CLOSED, or ON_HOLD (needs a reason) and back to IN_PROGRESS. CANCELLED ends an incident that was never needed.
 */
public enum IncidentState {
    NEW,
    IN_PROGRESS,
    ON_HOLD,
    RESOLVED,
    CLOSED,
    CANCELLED;

    /** An incident is "open" while someone still has work to do on it. An incident on hold is still open. */
    public boolean isOpen() {
        return this == NEW || this == IN_PROGRESS || this == ON_HOLD;
    }
}
