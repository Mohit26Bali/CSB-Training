package com.icc.servicenow.mapping;

import java.util.Map;

/**
 * Our states and codes as the numbers and values ServiceNow stores (read from the instance, 2026-11-26).
 * They are text here, so this library needs no class of the services (a library never depends on a service).
 */
public final class StateMapping {

    private static final Map<String, String> INCIDENT_STATE = Map.of(
            "NEW", "1",
            "IN_PROGRESS", "2",
            "ON_HOLD", "3",
            "RESOLVED", "6",
            "CLOSED", "7",
            "CANCELLED", "8");

    private static final Map<String, String> HOLD_REASON = Map.of(
            "AWAITING_CALLER", "1",
            "AWAITING_PROBLEM", "3",
            "AWAITING_VENDOR", "4",
            "AWAITING_CHANGE", "5");

    private static final Map<String, String> PROBLEM_STATE = Map.of(
            "NEW", "101",
            "ASSESS", "102",
            "ROOT_CAUSE_ANALYSIS", "103",
            "FIX_IN_PROGRESS", "104",
            "RESOLVED", "106",
            "CLOSED", "107");

    private static final Map<String, String> PROBLEM_RESOLUTION = Map.of(
            "FIX_APPLIED", "fix_applied",
            "RISK_ACCEPTED", "risk_accepted",
            "CANCELED", "canceled",
            "DUPLICATE", "duplicate");

    private StateMapping() {
    }

    public static String incidentState(String ours) {
        return lookup(INCIDENT_STATE, ours, "incident state");
    }

    public static String holdReason(String ours) {
        return lookup(HOLD_REASON, ours, "hold reason");
    }

    public static String problemState(String ours) {
        return lookup(PROBLEM_STATE, ours, "problem state");
    }

    public static String problemResolution(String ours) {
        return lookup(PROBLEM_RESOLUTION, ours, "problem resolution code");
    }

    private static String lookup(Map<String, String> table, String key, String what) {
        String value = table.get(key);
        if (value == null) {
            throw new IllegalArgumentException("No ServiceNow value for " + what + " " + key + ". Known: " + table.keySet());
        }
        return value;
    }
}
