package com.icc.majorincident.entity;

/** DECLARED, then MITIGATING, then RESOLVED, then CLOSED. Always in this order (ARCHITECTURE). */
public enum MajorIncidentStatus {
    DECLARED,
    MITIGATING,
    RESOLVED,
    CLOSED;

    /** The only status this one may move to, or null when it is the last one. */
    public MajorIncidentStatus next() {
        return switch (this) {
            case DECLARED -> MITIGATING;
            case MITIGATING -> RESOLVED;
            case RESOLVED -> CLOSED;
            case CLOSED -> null;
        };
    }

    public boolean isActive() {
        return this == DECLARED || this == MITIGATING;
    }
}
