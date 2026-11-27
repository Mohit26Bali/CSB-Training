package com.springlab.lab.trace;

import java.time.Instant;
import java.util.List;

/** The LIVE scenario: same step shape as the SIMULATED scenarios authored in the frontend. */
public record LifecycleTrace(
        String id,
        String mode,
        String method,
        String path,
        Instant capturedAt,
        int status,
        String contentType,
        long serverDurationMicros,
        boolean handlerInvoked,
        int sqlCount,
        List<SqlStatement> sql,
        List<TraceStep> steps,
        List<String> limitations) {

    public record Evidence(String label, String value) {
    }

    public record SqlStatement(int index, String sql, String phase, String trigger) {
    }

    /**
     * One teaching step. {@code nodes}/{@code edgeFrom}/{@code edgeTo} refer to the fixed diagram of the module;
     * {@code direction} is REQUEST or RESPONSE (which way the data moves along the edge).
     */
    public record TraceStep(
            String id,
            String title,
            List<String> nodes,
            String edgeFrom,
            String edgeTo,
            String direction,
            Provenance provenance,
            String explanation,
            List<Evidence> evidence,
            List<SqlStatement> sql,
            String codeRefId) {
    }
}
