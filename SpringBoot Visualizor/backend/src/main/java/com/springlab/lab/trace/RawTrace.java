package com.springlab.lab.trace;

import java.util.List;

/**
 * Everything observed while one traced request ran, before it is turned into teaching steps.
 * Deliberately contains no header values, tokens, request/response bodies, method arguments or bound SQL parameters.
 * The only header text kept is the server's own {@code WWW-Authenticate} challenge (never containing the token).
 */
public record RawTrace(
        String method,
        String path,
        boolean authorizationHeaderPresent,
        List<String> securityFilters,
        HandlerInfo handler,
        AuthInfo authentication,
        List<CallEvent> calls,
        List<SqlEvent> sql,
        int status,
        String contentType,
        long durationNanos,
        String authorizationScheme,
        String wwwAuthenticate) {

    /** Constructor used by the REST lifecycle module, which does not look at the scheme or the challenge. */
    public RawTrace(String method, String path, boolean authorizationHeaderPresent, List<String> securityFilters,
                    HandlerInfo handler, AuthInfo authentication, List<CallEvent> calls, List<SqlEvent> sql,
                    int status, String contentType, long durationNanos) {
        this(method, path, authorizationHeaderPresent, securityFilters, handler, authentication, calls, sql, status,
                contentType, durationNanos, null, null);
    }

    public record HandlerInfo(String controller, String method, String pattern) {
    }

    public record AuthInfo(String name, List<String> authorities) {
    }

    /** One service or repository method call made through its Spring proxy. {@code index} is its start order. */
    public record CallEvent(
            int index,
            Layer layer,
            String type,
            String method,
            Phase phase,
            long startNanos,
            long durationNanos,
            String transactional,
            String thrown,
            int depth) {
        public String label() {
            return type + "." + method;
        }
    }

    /** SQL text exactly as Hibernate handed it to JDBC: placeholders only, never bound values. */
    public record SqlEvent(String sql, Phase phase, int callIndex, String trigger, long offsetNanos) {
    }

    public boolean handlerInvoked() {
        return handler != null;
    }
}
