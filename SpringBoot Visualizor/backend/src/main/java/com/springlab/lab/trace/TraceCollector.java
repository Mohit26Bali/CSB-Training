package com.springlab.lab.trace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Collects observations for ONE traced request. It is bound to the request thread through {@link TraceContext}
 * and never shared between threads, so it needs no synchronization.
 */
public final class TraceCollector {

    private final long startNanos = System.nanoTime();
    private Phase phase = Phase.SECURITY;
    private RawTrace.HandlerInfo handler;
    private RawTrace.AuthInfo authentication;
    private final List<OpenCall> calls = new ArrayList<>();
    private final Deque<OpenCall> stack = new ArrayDeque<>();
    private final List<RawTrace.SqlEvent> sql = new ArrayList<>();

    /** Handle for a call in progress. */
    public static final class OpenCall {
        private final int index;
        private final Layer layer;
        private final String type;
        private final String method;
        private final Phase phase;
        private final long start;
        private final String transactional;
        private final int depth;
        private long durationNanos;
        private String thrown;

        private OpenCall(int index, Layer layer, String type, String method, Phase phase, long start,
                         String transactional, int depth) {
            this.index = index;
            this.layer = layer;
            this.type = type;
            this.method = method;
            this.phase = phase;
            this.start = start;
            this.transactional = transactional;
            this.depth = depth;
        }

        String label() {
            return type + "." + method;
        }
    }

    public OpenCall enter(Layer layer, String type, String method, String transactional) {
        OpenCall call = new OpenCall(calls.size(), layer, type, method, phase, System.nanoTime(), transactional,
                stack.size());
        calls.add(call);
        stack.push(call);
        return call;
    }

    public void exit(OpenCall call, Throwable thrown) {
        call.durationNanos = System.nanoTime() - call.start;
        call.thrown = thrown == null ? null : thrown.getClass().getSimpleName(); // class name only, never the message
        stack.remove(call);
    }

    /** The controller method was entered: everything from now on belongs to the HANDLER phase. */
    public void handlerEntered(RawTrace.HandlerInfo handlerInfo, RawTrace.AuthInfo authInfo) {
        this.handler = handlerInfo;
        this.authentication = authInfo;
        this.phase = Phase.HANDLER;
    }

    /** Called by the Hibernate StatementInspector for every statement Hibernate is about to prepare. */
    public void recordSql(String statement) {
        OpenCall owner = null;
        for (OpenCall candidate : stack) { // iterates from the innermost (most recently entered) call outwards
            if (candidate.layer == Layer.REPOSITORY) {
                owner = candidate;
                break;
            }
        }
        if (owner == null) {
            owner = stack.peek();
        }
        sql.add(new RawTrace.SqlEvent(statement, phase, owner == null ? -1 : owner.index,
                owner == null ? null : owner.label(), System.nanoTime() - startNanos));
    }

    /** Number of statements recorded so far (used by experiments to attribute statements to the step that caused them). */
    public int sqlCount() {
        return sql.size();
    }

    /** The statements recorded from position {@code from} on, in the order Hibernate prepared them. */
    public List<RawTrace.SqlEvent> sqlSince(int from) {
        return List.copyOf(sql.subList(from, sql.size()));
    }

    public RawTrace build(String method, String path, boolean authorizationHeaderPresent, List<String> securityFilters,
                          int status, String contentType) {
        return build(method, path, authorizationHeaderPresent, securityFilters, status, contentType, null, null);
    }

    public RawTrace build(String method, String path, boolean authorizationHeaderPresent, List<String> securityFilters,
                          int status, String contentType, String authorizationScheme, String wwwAuthenticate) {
        List<RawTrace.CallEvent> events = new ArrayList<>();
        for (OpenCall c : calls) {
            events.add(new RawTrace.CallEvent(c.index, c.layer, c.type, c.method, c.phase, c.start - startNanos,
                    c.durationNanos, c.transactional, c.thrown, c.depth));
        }
        return new RawTrace(method, path, authorizationHeaderPresent, List.copyOf(securityFilters), handler,
                authentication, List.copyOf(events), List.copyOf(sql), status, contentType,
                System.nanoTime() - startNanos, authorizationScheme, wwwAuthenticate);
    }
}
