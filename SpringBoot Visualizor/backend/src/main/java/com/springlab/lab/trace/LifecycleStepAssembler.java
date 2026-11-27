package com.springlab.lab.trace;

import com.springlab.lab.trace.LifecycleTrace.Evidence;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static com.springlab.lab.trace.Provenance.CAPTURED;
import static com.springlab.lab.trace.Provenance.CONCEPTUAL;
import static com.springlab.lab.trace.Provenance.DERIVED;

/**
 * Turns the raw observations of a request into ordered teaching steps. This is a pure function of its input, so every
 * rule below is unit-tested. The rule that matters most: a step is CAPTURED only if every fact in its evidence was
 * observed; anything inferred is DERIVED and anything that merely explains the framework is CONCEPTUAL.
 */
@Component
public class LifecycleStepAssembler {

    static final List<String> LIMITATIONS = List.of(
            "SQL shown is the text Hibernate prepared, with ? placeholders. Bound values are not captured, and the text does not show what the database returned or how it executed the statement.",
            "The security filters listed are the chain configured for this URL (read from FilterChainProxy). The execution of each individual filter is not separately observed.",
            "Framework internals such as HandlerMapping lookup, HandlerAdapter invocation and HttpMessageConverter selection are not observed. Steps about them are DERIVED or CONCEPTUAL.",
            "Only calls made through Spring proxies (services and repositories) are observed; internal self-invocations are not. Timings are single wall-clock measurements on this server and vary between runs.");

    public LifecycleTrace assemble(String id, RawTrace raw) {
        List<TraceStep> steps = new ArrayList<>();
        List<SqlStatement> allSql = new ArrayList<>();
        for (int i = 0; i < raw.sql().size(); i++) {
            RawTrace.SqlEvent e = raw.sql().get(i);
            allSql.add(new SqlStatement(i + 1, e.sql(), e.phase().name(), e.trigger()));
        }

        steps.add(clientRequest(raw));
        steps.add(securityChain(raw));

        // Repository calls made before any controller was entered (the database-backed authentication lookup).
        for (RawTrace.CallEvent call : raw.calls()) {
            if (call.phase() == Phase.SECURITY && call.layer() == Layer.REPOSITORY) {
                steps.add(authenticationLookup(call));
                addSqlAndDatabaseSteps(steps, raw, call, allSql);
            }
        }

        if (!raw.handlerInvoked()) {
            steps.add(rejectedBeforeController(raw));
            steps.add(rejectionMechanism(raw));
        } else {
            steps.add(dispatch(raw));
            steps.add(controller(raw));
            for (RawTrace.CallEvent call : raw.calls()) {
                if (call.phase() != Phase.HANDLER) {
                    continue;
                }
                steps.add(call.layer() == Layer.SERVICE ? service(call) : repository(call));
                if (call.layer() == Layer.REPOSITORY) {
                    addSqlAndDatabaseSteps(steps, raw, call, allSql);
                }
            }
            String thrown = firstThrown(raw);
            if (thrown != null && raw.status() >= 400) {
                steps.add(exceptionHandling(raw, thrown));
            } else if (raw.contentType() != null && raw.contentType().contains("json")) {
                steps.add(serialization(raw));
            }
        }
        steps.add(response(raw));

        return new LifecycleTrace(id, "LIVE", raw.method(), raw.path(), Instant.now(), raw.status(), raw.contentType(),
                raw.durationNanos() / 1_000, raw.handlerInvoked(), allSql.size(), List.copyOf(allSql),
                List.copyOf(steps), LIMITATIONS);
    }

    // ------------------------------------------------------------------ individual steps

    private TraceStep clientRequest(RawTrace raw) {
        return step("client-request", "The browser sends the HTTP request", List.of("client"), "client",
                "securityFilters", "REQUEST", CAPTURED,
                "A real HTTP request reached SpringLab's embedded Tomcat. Only whether an Authorization header was "
                        + "present is recorded; the token itself is never stored in a trace.",
                List.of(ev("Method", raw.method()), ev("Path", raw.path()),
                        ev("Authorization header", raw.authorizationHeaderPresent() ? "present (value not recorded)"
                                : "absent")),
                null, null);
    }

    private TraceStep securityChain(RawTrace raw) {
        return step("security-filter-chain", "Spring Security filter chain", List.of("securityFilters"), null, null,
                "REQUEST", CAPTURED,
                "Before DispatcherServlet sees a request, it passes through the SecurityFilterChain (via "
                        + "FilterChainProxy). The list shows the filters configured for this URL. SpringLab validates "
                        + "bearer tokens with a JwtDecoder and takes the caller's authorities from the database.",
                List.of(ev("Configured filters (" + raw.securityFilters().size() + ")",
                        String.join(" → ", raw.securityFilters()))),
                null, CodeRefIds.SECURITY_FILTER_CHAIN);
    }

    private TraceStep authenticationLookup(RawTrace.CallEvent call) {
        return step("auth-lookup-" + call.index(), "Before any controller: " + call.label(),
                List.of("securityFilters", "repository"), "securityFilters", "repository", "REQUEST", CAPTURED,
                "This repository call happened before a controller was entered. In SpringLab it is the "
                        + "DatabaseBackedJwtAuthenticationConverter reading the caller's current role and enabled flag "
                        + "after the token's signature, expiry and issuer were validated (see the code).",
                List.of(ev("Repository method", call.label()), ev("Duration", micros(call.durationNanos()))),
                null, CodeRefIds.forMethod(call.type(), call.method()));
    }

    private void addSqlAndDatabaseSteps(List<TraceStep> steps, RawTrace raw, RawTrace.CallEvent call,
                                        List<SqlStatement> allSql) {
        List<SqlStatement> owned = new ArrayList<>();
        for (int i = 0; i < raw.sql().size(); i++) {
            if (raw.sql().get(i).callIndex() == call.index()) {
                owned.add(allSql.get(i));
            }
        }
        if (owned.isEmpty()) {
            return;
        }
        steps.add(step("sql-" + call.index(), "Hibernate prepares " + owned.size() + " SQL statement"
                        + (owned.size() == 1 ? "" : "s"), List.of("hibernate"), "repository", "hibernate", "REQUEST",
                CAPTURED,
                "The statement text below is what Hibernate handed to JDBC for this repository call. It contains "
                        + "placeholders (?) instead of values, and it does not prove how the database executed it.",
                List.of(ev("Statements", String.valueOf(owned.size()))), owned, CodeRefIds.SQL_CAPTURE));
        steps.add(step("database-" + call.index(), "MySQL executes the statement", List.of("database"), "hibernate",
                "database", "REQUEST", CONCEPTUAL,
                "Hibernate sends the prepared statement to MySQL through JDBC, binds the parameters, and reads the "
                        + "result set. SpringLab does not capture bound values, rows or database execution details, "
                        + "so this step explains the framework rather than reporting an observation.",
                List.of(), null, null));
    }

    private TraceStep rejectedBeforeController(RawTrace raw) {
        return step("rejected-before-controller", "Rejected before reaching a controller (HTTP " + raw.status() + ")",
                List.of("securityFilters"), "client", "securityFilters", "RESPONSE", CAPTURED,
                "DispatcherServlet never selected a handler for this request: no controller, service or repository "
                        + "method of the application ran. The response was produced inside the security filter chain.",
                List.of(ev("Status", String.valueOf(raw.status())), ev("Controller invoked", "no"),
                        ev("Service calls", String.valueOf(count(raw, Layer.SERVICE))),
                        ev("SQL statements prepared", String.valueOf(raw.sql().size()))),
                null, null);
    }

    private TraceStep rejectionMechanism(RawTrace raw) {
        boolean unauthorized = raw.status() == 401;
        String text = unauthorized
                ? "Inferred mechanism: no valid authentication was established, so Spring Security's "
                + "ExceptionTranslationFilter delegated to the AuthenticationEntryPoint, which in SpringLab "
                + "(RestAuthenticationEntryPoint) writes a 401 application/problem+json response."
                : "Inferred mechanism: the caller was authenticated but not authorized, so the "
                + "ExceptionTranslationFilter delegated to the AccessDeniedHandler, which in SpringLab "
                + "(RestAccessDeniedHandler) writes a 403 application/problem+json response.";
        return step("rejection-mechanism", unauthorized ? "Why 401: AuthenticationEntryPoint"
                        : raw.status() == 403 ? "Why 403: AccessDeniedHandler" : "Why this status",
                List.of("securityFilters"), null, null, "RESPONSE", DERIVED,
                text + " Which class wrote the response is inferred from the observed status and content type; it is "
                        + "not captured directly.",
                rejectionEvidence(raw),
                null, unauthorized ? CodeRefIds.ENTRY_POINT
                        : raw.status() == 403 ? CodeRefIds.ACCESS_DENIED_HANDLER : null);
    }

    private TraceStep dispatch(RawTrace raw) {
        RawTrace.HandlerInfo h = raw.handler();
        RawTrace.AuthInfo a = raw.authentication();
        List<Evidence> evidence = new ArrayList<>();
        if (a != null) {
            evidence.add(ev("Authenticated as", a.name()));
            evidence.add(ev("Authorities", a.authorities().isEmpty() ? "(none)" : String.join(", ", a.authorities())));
        }
        evidence.add(ev("Selected handler", h.controller() + "#" + h.method()));
        evidence.add(ev("Matched mapping", String.valueOf(h.pattern())));
        return step("dispatch", "DispatcherServlet selects the handler", List.of("dispatcher"), "securityFilters",
                "dispatcher", "REQUEST", CAPTURED,
                "The request passed the security filters and reached DispatcherServlet, which used a HandlerMapping to "
                        + "choose the controller method below. The selected handler and mapping were observed; the "
                        + "internal lookup itself is not.",
                evidence, null, null);
    }

    private TraceStep controller(RawTrace raw) {
        RawTrace.HandlerInfo h = raw.handler();
        return step("controller", "Controller: " + h.controller() + "." + h.method(), List.of("controller"),
                "dispatcher", "controller", "REQUEST", CAPTURED,
                "A HandlerAdapter invoked the controller method (argument resolution, @AuthenticationPrincipal and "
                        + "validation happen around this call). Controllers delegate to services.",
                List.of(ev("Handler method", h.controller() + "#" + h.method())), null,
                CodeRefIds.forMethod(h.controller(), h.method()));
    }

    private TraceStep service(RawTrace.CallEvent call) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(ev("Service method", call.label()));
        evidence.add(ev("@Transactional on this method", call.transactional() == null ? "not declared"
                : call.transactional()));
        evidence.add(ev("Duration", micros(call.durationNanos())));
        if (call.thrown() != null) {
            evidence.add(ev("Threw", call.thrown()));
        }
        return step("service-" + call.index(), "Service: " + call.label(), List.of("service"), "controller",
                "service", "REQUEST", CAPTURED,
                "The call reached the service through its Spring proxy. The @Transactional line is read from the "
                        + "source annotation; it does not report the live state of the transaction.",
                evidence, null, CodeRefIds.forMethod(call.type(), call.method()));
    }

    private TraceStep repository(RawTrace.CallEvent call) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(ev("Repository method", call.label()));
        evidence.add(ev("Duration", micros(call.durationNanos())));
        if (call.thrown() != null) {
            evidence.add(ev("Threw", call.thrown()));
        }
        return step("repository-" + call.index(), "Repository: " + call.label(), List.of("repository"), "service",
                "repository", "REQUEST", CAPTURED,
                "The service called a Spring Data repository (a proxy generated from the interface). Its query methods "
                        + "are executed through JPA and Hibernate.",
                evidence, null, CodeRefIds.forMethod(call.type(), call.method()));
    }

    private TraceStep exceptionHandling(RawTrace raw, String thrown) {
        return step("exception-handling", "Exception translated to an HTTP error response", List.of("dispatcher"),
                "dispatcher", "controller", "RESPONSE", DERIVED,
                "A service threw " + thrown + " and the response status is " + raw.status() + ". DispatcherServlet's "
                        + "exception resolvers delegate to SpringLab's @RestControllerAdvice (GlobalExceptionHandler), "
                        + "which maps this exception to a ProblemDetail. That the advice produced the response is "
                        + "inferred from the observed exception, status and content type; the matching handler method "
                        + "is not captured.",
                List.of(ev("Exception observed in service", thrown), ev("Observed status", String.valueOf(raw.status())),
                        ev("Observed content type", String.valueOf(raw.contentType()))),
                null, "ResourceNotFoundException".equals(thrown) ? CodeRefIds.NOT_FOUND_HANDLER : null);
    }

    private TraceStep serialization(RawTrace raw) {
        return step("serialization", "The response body is written as JSON", List.of("dispatcher"), "dispatcher",
                "controller", "RESPONSE", DERIVED,
                "The controller returned an object, which an HttpMessageConverter wrote to the response body. The "
                        + "response content type indicates JSON, so a JSON converter (Jackson, in a standard Spring "
                        + "Boot application) did the work; the converter class itself is not captured.",
                List.of(ev("Observed content type", String.valueOf(raw.contentType()))), null, null);
    }

    private TraceStep response(RawTrace raw) {
        return step("response", "The response returns to the browser", List.of("client"), "client",
                "securityFilters", "RESPONSE", CAPTURED,
                "The response travelled back out through the filter chain. The status and content type were read "
                        + "after the whole chain finished.",
                List.of(ev("Status", String.valueOf(raw.status())),
                        ev("Content type", String.valueOf(raw.contentType())),
                        ev("Server time", micros(raw.durationNanos())),
                        ev("SQL statements prepared by Hibernate", String.valueOf(raw.sql().size()))),
                null, null);
    }

    // ------------------------------------------------------------------ helpers

    private static TraceStep step(String id, String title, List<String> nodes, String edgeFrom, String edgeTo,
                                  String direction, Provenance provenance, String explanation, List<Evidence> evidence,
                                  List<SqlStatement> sql, String codeRefId) {
        return new TraceStep(id, title, nodes, edgeFrom, edgeTo, direction, provenance, explanation, evidence,
                sql == null ? List.of() : sql, codeRefId);
    }

    private static Evidence ev(String label, String value) {
        return new Evidence(label, value);
    }

    private static List<Evidence> rejectionEvidence(RawTrace raw) {
        List<Evidence> evidence = new ArrayList<>();
        evidence.add(ev("Observed status", String.valueOf(raw.status())));
        evidence.add(ev("Observed content type", String.valueOf(raw.contentType())));
        if (raw.wwwAuthenticate() != null) {
            evidence.add(ev("Observed WWW-Authenticate header", raw.wwwAuthenticate()));
        }
        return evidence;
    }

    private static String micros(long nanos) {
        return (nanos / 1_000) + " µs";
    }

    private static long count(RawTrace raw, Layer layer) {
        return raw.calls().stream().filter(c -> c.layer() == layer).count();
    }

    private static String firstThrown(RawTrace raw) {
        return raw.calls().stream().filter(c -> c.phase() == Phase.HANDLER && c.thrown() != null)
                .map(RawTrace.CallEvent::thrown).findFirst().orElse(null);
    }
}
