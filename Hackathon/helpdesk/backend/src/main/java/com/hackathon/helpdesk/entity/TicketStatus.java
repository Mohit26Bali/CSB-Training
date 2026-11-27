package com.hackathon.helpdesk.entity;

import java.util.List;

/**
 * OPEN -> IN_PROGRESS -> RESOLVED -> CLOSED. A resolved ticket can go back to IN_PROGRESS if the fix did not hold.
 * CLOSED is final: closed tickets cannot be reopened. The forward step is listed first.
 */
public enum TicketStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public List<TicketStatus> next() {
        return switch (this) {
            case OPEN -> List.of(IN_PROGRESS);
            case IN_PROGRESS -> List.of(RESOLVED);
            case RESOLVED -> List.of(CLOSED, IN_PROGRESS);
            case CLOSED -> List.of();
        };
    }

    public boolean isActive() {
        return this == OPEN || this == IN_PROGRESS;
    }
}
