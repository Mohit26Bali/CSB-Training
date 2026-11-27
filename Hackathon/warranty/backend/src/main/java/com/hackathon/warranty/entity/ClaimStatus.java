package com.hackathon.warranty.entity;

/** PENDING -> APPROVED or REJECTED; an approved claim goes IN_REPAIR and then COMPLETED. */
public enum ClaimStatus {
    PENDING,
    APPROVED,
    REJECTED,
    IN_REPAIR,
    COMPLETED;

    /** A claim that is still open: it blocks another claim for the same product. */
    public boolean isActive() {
        return this == PENDING || this == APPROVED || this == IN_REPAIR;
    }
}
