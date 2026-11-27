package com.hackathon.event.entity;

import java.util.EnumSet;
import java.util.Set;

public enum RegistrationStatus {
    CONFIRMED,
    WAITLISTED,
    CANCELLED;

    /** The statuses that count as a live registration. */
    public static final Set<RegistrationStatus> ACTIVE = EnumSet.of(CONFIRMED, WAITLISTED);
}
