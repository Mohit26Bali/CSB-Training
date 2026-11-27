package com.icc.servicenow.mapping;

import java.util.List;

/**
 * The close code of an incident (our resolution code). ServiceNow accepts only its ten values and answers any other with a
 * confusing HTTP 500, so we refuse a wrong value here, before the call, with a clear message (D36, D37).
 * The values are the same text on both sides, so the mapping only guards.
 */
public final class ResolutionMapping {

    public static final List<String> CLOSE_CODES = List.of(
            "Duplicate", "Known error", "No resolution provided", "Resolved by caller", "Resolved by change",
            "Resolved by problem", "Resolved by request", "Solution provided", "Workaround provided", "User error");

    private ResolutionMapping() {
    }

    /** The close code to send, or an IllegalArgumentException when it is not one of the ten. */
    public static String closeCode(String ours) {
        if (ours == null || !CLOSE_CODES.contains(ours)) {
            throw new IllegalArgumentException("\"" + ours + "\" is not a ServiceNow close code. Allowed: " + CLOSE_CODES);
        }
        return ours;
    }
}
