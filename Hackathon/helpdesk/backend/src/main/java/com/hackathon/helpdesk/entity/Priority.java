package com.hackathon.helpdesk.entity;

/** Priority and the hours the support team has to resolve a ticket of that priority (the SLA). */
public enum Priority {
    LOW(72),
    MEDIUM(24),
    HIGH(8),
    CRITICAL(4);

    private final int slaHours;

    Priority(int slaHours) {
        this.slaHours = slaHours;
    }

    public int getSlaHours() {
        return slaHours;
    }
}
