package com.icc.incident.service;

import com.icc.common.error.BadRequestException;

import java.util.List;

/**
 * The fixed list of resolution codes (D36). They are the ten close codes of the ServiceNow instance, spelled exactly as
 * it spells them: the instance rejects any other value, so the app accepts only these. Free text is not allowed.
 */
public final class ResolutionCodes {

    public static final List<String> ALL = List.of(
            "Duplicate", "Known error", "No resolution provided", "Resolved by caller", "Resolved by change",
            "Resolved by problem", "Resolved by request", "Solution provided", "Workaround provided", "User error");

    private ResolutionCodes() {
    }

    /** Returns the code in its official spelling (the match ignores upper and lower case), or fails with a 400. */
    public static String require(String code) {
        String wanted = code == null ? "" : code.trim();
        return ALL.stream()
                .filter(known -> known.equalsIgnoreCase(wanted))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Unknown resolution code " + code + ". Allowed: " + ALL));
    }
}
