package com.hackathon.maintenance.entity;

import java.util.List;

/**
 * OPEN -> ASSIGNED -> IN_PROGRESS -> RESOLVED -> CLOSED. OPEN becomes ASSIGNED only by assigning a technician; a resolved
 * request can go back to IN_PROGRESS when the work needs redoing. CLOSED is final.
 */
public enum RequestStatus {
    OPEN,
    ASSIGNED,
    IN_PROGRESS,
    RESOLVED,
    CLOSED;

    public List<RequestStatus> next() {
        return switch (this) {
            case OPEN -> List.of(ASSIGNED);
            case ASSIGNED -> List.of(IN_PROGRESS);
            case IN_PROGRESS -> List.of(RESOLVED);
            case RESOLVED -> List.of(CLOSED, IN_PROGRESS);
            case CLOSED -> List.of();
        };
    }

    public boolean isActive() {
        return this == OPEN || this == ASSIGNED || this == IN_PROGRESS;
    }
}
