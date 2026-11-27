package com.hackathon.referral.entity;

import java.util.EnumSet;
import java.util.Set;

/**
 * SUBMITTED -> SCREENING -> INTERVIEW -> SELECTED, one step at a time. REJECTED can happen from any stage that is
 * not finished yet. SELECTED and REJECTED are final.
 */
public enum ReferralStatus {
    SUBMITTED,
    SCREENING,
    INTERVIEW,
    SELECTED,
    REJECTED;

    /** The stages this one may move to. */
    public Set<ReferralStatus> next() {
        return switch (this) {
            case SUBMITTED -> EnumSet.of(SCREENING, REJECTED);
            case SCREENING -> EnumSet.of(INTERVIEW, REJECTED);
            case INTERVIEW -> EnumSet.of(SELECTED, REJECTED);
            case SELECTED, REJECTED -> EnumSet.noneOf(ReferralStatus.class);
        };
    }

    public boolean isFinal() {
        return next().isEmpty();
    }
}
