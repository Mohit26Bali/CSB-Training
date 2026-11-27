package com.icc.incident.repository;

import com.icc.common.error.BadRequestException;

/**
 * Which incidents a list should show (ARCHITECTURE).
 * MINE: I reported them. ASSIGNED: assigned to me, or unassigned in my team (my queue). ALL: every incident.
 */
public enum IncidentScope {
    MINE,
    ASSIGNED,
    ALL;

    public static IncidentScope parse(String text) {
        try {
            return valueOf(text.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new BadRequestException("Unknown scope " + text + ". Use mine, assigned or all");
        }
    }
}
