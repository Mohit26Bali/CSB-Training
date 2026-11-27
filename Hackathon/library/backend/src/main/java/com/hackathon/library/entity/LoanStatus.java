package com.hackathon.library.entity;

import java.util.EnumSet;
import java.util.Set;

public enum LoanStatus {
    RESERVED,
    ISSUED,
    RETURNED,
    CANCELLED;

    /** The statuses where the member still holds one of the book's copies. */
    public static final Set<LoanStatus> HOLDING = EnumSet.of(RESERVED, ISSUED);
}
