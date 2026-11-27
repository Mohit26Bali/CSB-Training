package com.icc.incident.entity;

/** Why an incident is ON_HOLD: the same four reasons as ServiceNow. */
public enum HoldReason {
    AWAITING_CALLER,
    AWAITING_PROBLEM,
    AWAITING_VENDOR,
    AWAITING_CHANGE
}
