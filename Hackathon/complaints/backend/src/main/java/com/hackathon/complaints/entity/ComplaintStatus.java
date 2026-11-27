package com.hackathon.complaints.entity;

import java.util.List;

/** OPEN, IN_PROGRESS, RESOLVED, CLOSED. A resolved complaint can go back to IN_PROGRESS; a closed one is final. */
public enum ComplaintStatus {
    OPEN,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public List<ComplaintStatus> next() {
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
