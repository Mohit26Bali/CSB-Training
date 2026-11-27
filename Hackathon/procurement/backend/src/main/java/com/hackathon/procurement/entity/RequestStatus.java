package com.hackathon.procurement.entity;

/** PENDING -> APPROVED or REJECTED; an approved request is then ORDERED and finally DELIVERED. */
public enum RequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    ORDERED,
    DELIVERED;

    /** Approved, ordered or delivered: money the company has committed to spend. */
    public boolean isCommitted() {
        return this == APPROVED || this == ORDERED || this == DELIVERED;
    }
}
