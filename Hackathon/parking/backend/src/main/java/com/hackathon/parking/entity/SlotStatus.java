package com.hackathon.parking.entity;

/** What the admin marks a slot as. Whether it is taken is worked out from the allocations. */
public enum SlotStatus {
    AVAILABLE,
    RESERVED,
    MAINTENANCE
}
