package com.springlab.lab.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Traces a real request when (and only when) the caller opts in with {@code X-SpringLab-Trace: <ticket>} on a GET that one
 * of the learning modules ({@link TraceModule}) lists. The ticket comes from {@link TraceTicketStore}: it is single-use
 * and bound to the authenticated user who requested it, who becomes the owner of the stored trace. A missing, unknown,
 * expired or already-used ticket means the request is simply not traced and passes straight through untouched.
 *
 * <p>It runs just before the Spring Security filter chain (order -101, security is -100) so that the whole chain,
 * including the authentication step, happens inside the observed window. The server answers with
 * {@code X-SpringLab-Trace-Id}; only the owner can fetch the assembled trace. Header values, bodies and tokens are never
 * recorded.
 */
@Component
@Order(-101)
public class LifecycleTraceFilter extends OncePerRequestFilter {

    public static final String REQUEST_HEADER = "X-SpringLab-Trace";
    public static final String RESPONSE_HEADER = "X-SpringLab-Trace-Id";
    private static final int MAX_CHALLENGE_LENGTH = 400;

    private final FilterChainProxy securityFilterChain;
    private final List<TraceModule> modules;
    private final TraceStore store;
    private final TraceTicketStore tickets;

    public LifecycleTraceFilter(FilterChainProxy securityFilterChain, List<TraceModule> modules, TraceStore store,
                                TraceTicketStore tickets) {
        this.securityFilterChain = securityFilterChain;
        this.modules = modules;
        this.store = store;
        this.tickets = tickets;
    }

    private Optional<TraceModule> moduleFor(HttpServletRequest request) {
        String header = request.getHeader(REQUEST_HEADER);
        if (header == null || header.isBlank()) {
            return Optional.empty();
        }
        return modules.stream().filter(m -> m.handles(request.getMethod(), request.getRequestURI())).findFirst();
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return moduleFor(request).isEmpty();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        TraceModule module = moduleFor(request).orElseThrow();
        // The ticket is consumed only now, for a request that is actually traceable. No valid ticket: not traced.
        OptionalLong owner = tickets.redeem(request.getHeader(REQUEST_HEADER).trim());
        if (owner.isEmpty()) {
            chain.doFilter(request, response);
            return;
        }
        String id = UUID.randomUUID().toString();
        String path = request.getRequestURI();
        String authorization = request.getHeader("Authorization");
        // Reading the configured chain does not execute any filter.
        List<String> filters = securityFilterChain.getFilters(path).stream()
                .map(filter -> filter.getClass().getSimpleName()).toList();

        TraceCollector collector = new TraceCollector();
        response.setHeader(RESPONSE_HEADER, id); // set before the chain so it is present even on 401/403 responses
        TraceContext.bind(collector);
        try {
            chain.doFilter(request, response);
        } finally {
            TraceContext.clear();
            RawTrace raw = collector.build(request.getMethod(), path, authorization != null, filters,
                    response.getStatus(), response.getContentType(), schemeOf(authorization),
                    safeChallenge(response.getHeader("WWW-Authenticate"), authorization));
            store.put(module.assemble(id, raw), owner.getAsLong());
        }
    }

    /** Only the scheme word ("Bearer", "Basic") is kept, never the credentials after it. */
    static String schemeOf(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        int space = authorization.indexOf(' ');
        String scheme = space < 0 ? authorization : authorization.substring(0, space);
        return scheme.length() > 20 ? "(unrecognized)" : scheme;
    }

    /**
     * The server's own challenge header ({@code Bearer error="invalid_token", error_description="..."}) is genuine
     * evidence of WHY authentication failed. It is shortened, and dropped entirely if it ever contained the credentials.
     */
    static String safeChallenge(String challenge, String authorization) {
        if (challenge == null) {
            return null;
        }
        if (authorization != null) {
            int space = authorization.indexOf(' ');
            String credentials = space < 0 ? "" : authorization.substring(space + 1).trim();
            if (!credentials.isEmpty() && challenge.contains(credentials)) {
                return "[redacted: contained the credentials]";
            }
        }
        return challenge.length() > MAX_CHALLENGE_LENGTH ? challenge.substring(0, MAX_CHALLENGE_LENGTH) + "…" : challenge;
    }
}
