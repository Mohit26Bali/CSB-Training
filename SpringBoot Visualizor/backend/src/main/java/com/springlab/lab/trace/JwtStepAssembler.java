package com.springlab.lab.trace;

import com.springlab.lab.trace.LifecycleTrace.Evidence;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.springlab.lab.trace.Provenance.CAPTURED;
import static com.springlab.lab.trace.Provenance.DERIVED;

/**
 * Turns the observations of one request to a lab-secure endpoint into the JWT Security Simulator's steps.
 *
 * <p>What is OBSERVED (CAPTURED): the Authorization scheme (never the token), the configured filter chain, whether and
 * which repository call ran before a controller, the SQL text, the response status and the server's own
 * {@code WWW-Authenticate} challenge, whether a controller was reached and who the security context then held.
 * What is INFERRED (DERIVED): which component rejected the request. The decoder's individual checks and the
 * authorization rule evaluation are not observed, so steps about them say "inferred" and are never LIVE.
 */
@Component
public class JwtStepAssembler {

    static final List<String> LIMITATIONS = List.of(
            "The token itself is never recorded in a trace. The decoded token shown by the simulator comes from the separate inspection endpoint.",
            "Which component rejected a request (the JwtDecoder, the converter, or an authorization rule) is inferred from the status, the server's WWW-Authenticate challenge and whether a database lookup followed. The decoder's individual checks are not observed during the request.",
            "The security filters listed are the chain configured for this URL (read from FilterChainProxy). The execution of each filter is not separately observed.",
            "SQL shown is the text Hibernate prepared, with ? placeholders. Bound values are not captured, and the text does not show what the database returned or how it executed the statement.",
            "Lab endpoints use a lab signing key that exists only in memory and changes on every restart. They run the application's real converter, entry point and access-denied handler, with a separate decoder instance for the lab key.");

    private static final Pattern ERROR = Pattern.compile("error=\"([^\"]*)\"");
    private static final Pattern DESCRIPTION = Pattern.compile("error_description=\"([^\"]*)\"");
    private static final String LAB_PREFIX = "/api/lab/secure/";

    public LifecycleTrace assemble(String id, RawTrace raw) {
        boolean bearer = "Bearer".equalsIgnoreCase(raw.authorizationScheme());
        String challenge = raw.wwwAuthenticate();
        boolean invalidToken = challenge != null && "invalid_token".equals(group(ERROR, challenge));
        List<RawTrace.CallEvent> lookups = raw.calls().stream()
                .filter(c -> c.phase() == Phase.SECURITY && c.layer() == Layer.REPOSITORY).toList();
        boolean lookedUp = !lookups.isEmpty();
        boolean decoderRejected = bearer && invalidToken && !lookedUp;

        List<SqlStatement> allSql = new ArrayList<>();
        for (int i = 0; i < raw.sql().size(); i++) {
            RawTrace.SqlEvent e = raw.sql().get(i);
            allSql.add(new SqlStatement(i + 1, e.sql(), e.phase().name(), e.trigger()));
        }

        List<TraceStep> steps = new ArrayList<>();
        steps.add(clientRequest(raw, bearer));
        steps.add(filterChain(raw));

        if (!bearer) {
            steps.add(noBearerToken(raw));
            steps.add(anonymousDenied(raw));
            steps.add(outcome401(raw, true));
            steps.add(mechanism401(raw));
        } else {
            steps.add(tokenValidation(raw, decoderRejected, lookedUp));
            if (lookedUp) {
                steps.add(databaseLookup(lookups.get(0), raw, allSql));
            }
            if (decoderRejected) {
                steps.add(outcome401(raw, false));
                steps.add(mechanism401(raw));
            } else if (invalidToken) { // a lookup ran, then authentication was still refused
                steps.add(rejectedAfterLookup(raw));
                steps.add(outcome401(raw, false));
                steps.add(mechanism401(raw));
            } else if (lookedUp) {
                steps.add(authenticationEstablished(raw));
                steps.add(authorizationDecision(raw));
                if (raw.handlerInvoked()) {
                    steps.add(controllerReached(raw));
                } else {
                    steps.add(outcome403(raw));
                    steps.add(mechanism403(raw));
                }
            } else {
                steps.add(unclassified(raw));
            }
        }
        return new LifecycleTrace(id, "LIVE", raw.method(), raw.path(), Instant.now(), raw.status(), raw.contentType(),
                raw.durationNanos() / 1_000, raw.handlerInvoked(), allSql.size(), List.copyOf(allSql),
                List.copyOf(steps), LIMITATIONS);
    }

    // ------------------------------------------------------------------ steps

    private TraceStep clientRequest(RawTrace raw, boolean bearer) {
        String scheme = raw.authorizationScheme() == null ? "absent (no Authorization header)" : raw.authorizationScheme();
        return step("client-request", "The client sends the request", List.of("client"), "client", "bearerFilter",
                CAPTURED,
                bearer ? "A real GET request arrived with an Authorization: Bearer header. Only the scheme is recorded; "
                        + "the token itself never appears in a trace."
                        : "A real GET request arrived without a bearer token. Nothing in this request identifies a user.",
                List.of(ev("Method", raw.method()), ev("Path", raw.path()), ev("Authorization scheme", scheme),
                        ev("Token value", "never recorded")),
                null, null);
    }

    private TraceStep filterChain(RawTrace raw) {
        return step("security-chain", "Spring Security filter chain", List.of("bearerFilter"), null, null, CAPTURED,
                "The request enters the SecurityFilterChain configured for this URL. For /api/lab/secure/** that is the lab "
                        + "chain: the same kinds of filters as the application's main chain, with a JwtDecoder that "
                        + "verifies with the lab key.",
                List.of(ev("Configured filters (" + raw.securityFilters().size() + ")",
                        String.join(" → ", raw.securityFilters()))),
                null, raw.path().startsWith(LAB_PREFIX) ? CodeRefIds.LAB_SECURE_CHAIN : CodeRefIds.SECURITY_FILTER_CHAIN);
    }

    private TraceStep noBearerToken(RawTrace raw) {
        return step("no-bearer-token", "No bearer token to authenticate", List.of("bearerFilter"), null, null, DERIVED,
                "BearerTokenAuthenticationFilter only acts on an 'Authorization: Bearer ...' header. Without one it "
                        + "authenticates nothing and lets the request continue as anonymous. Inferred from what was "
                        + "observed: no bearer scheme, no database lookup, and a challenge that names no token error.",
                List.of(ev("Authorization scheme", raw.authorizationScheme() == null ? "absent" : raw.authorizationScheme()),
                        ev("Database lookups before any controller", "0"),
                        ev("Observed WWW-Authenticate", orNone(raw.wwwAuthenticate()))),
                null, CodeRefIds.SECURITY_FILTER_CHAIN);
    }

    private TraceStep anonymousDenied(RawTrace raw) {
        return step("anonymous-denied", "Authorization refuses an unauthenticated caller", List.of("authorization"), null,
                null, DERIVED,
                "The rules for this path require an authenticated caller. AuthorizationFilter denies the anonymous "
                        + "request and ExceptionTranslationFilter turns that into an authentication challenge (401), "
                        + "not a 403: nobody has been identified yet. Inferred from the 401 and the absence of a controller.",
                List.of(ev("Observed status", String.valueOf(raw.status())), ev("Controller reached", "no")),
                null, CodeRefIds.LAB_SECURE_CHAIN);
    }

    private TraceStep tokenValidation(RawTrace raw, boolean rejected, boolean lookedUp) {
        List<Evidence> evidence = new ArrayList<>();
        if (rejected) {
            evidence.add(ev("Observed WWW-Authenticate", orNone(raw.wwwAuthenticate())));
            String description = group(DESCRIPTION, raw.wwwAuthenticate());
            if (description != null) {
                evidence.add(ev("Reason given by the framework", description));
            }
            evidence.add(ev("Database lookups before any controller", "0"));
        } else {
            evidence.add(ev("Database lookup for the token's subject followed", lookedUp ? "yes" : "no"));
        }
        return step("token-validation", rejected ? "The JwtDecoder rejects the token" : "The JwtDecoder accepts the token",
                List.of("authManager", "jwtDecoder"), "authManager", "jwtDecoder", DERIVED,
                (rejected
                        ? "BearerTokenAuthenticationFilter handed the token to the AuthenticationManager, whose JwtAuthenticationProvider "
                        + "asks the JwtDecoder to parse it, check the algorithm and signature, then validate exp, nbf and iss. "
                        + "The challenge in the response is the framework's own explanation of the first check that failed. "
                        + "That the decoder rejected it is inferred from that challenge and from the fact that no database lookup happened."
                        : "The token passed signature and claim validation. This is inferred: the converter, which runs only after "
                        + "the decoder accepts a token, went on to look the user up in the database.")
                        + " The decoder's individual checks are not observed during the request.",
                evidence, null, CodeRefIds.JWT_DECODER_CONFIG);
    }

    private TraceStep databaseLookup(RawTrace.CallEvent call, RawTrace raw, List<SqlStatement> allSql) {
        List<SqlStatement> owned = new ArrayList<>();
        for (int i = 0; i < raw.sql().size(); i++) {
            if (raw.sql().get(i).callIndex() == call.index()) {
                owned.add(allSql.get(i));
            }
        }
        return step("database-lookup", "The caller's role and status are read from the database",
                List.of("converter", "database"), "converter", "database", CAPTURED,
                "Before any controller ran, the application's converter read this user's current role and enabled flag from "
                        + "the database. Authorization uses that stored role, not the role claim inside the token. The SQL "
                        + "text has placeholders and does not show what MySQL returned.",
                List.of(ev("Repository method", call.label()), ev("Duration", (call.durationNanos() / 1_000) + " µs"),
                        ev("SQL statements", String.valueOf(owned.size()))),
                owned, CodeRefIds.AUTH_CONVERTER);
    }

    private TraceStep rejectedAfterLookup(RawTrace raw) {
        return step("rejected-after-lookup", "Authentication refused after the database lookup", List.of("converter"),
                null, null, DERIVED,
                "The token was valid but the application's converter still refused it. SpringLab's converter throws "
                        + "InvalidBearerTokenException when the user no longer exists or the account is disabled. Inferred "
                        + "from a database lookup followed by an invalid_token challenge.",
                List.of(ev("Reason given by the framework", orNone(group(DESCRIPTION, raw.wwwAuthenticate())))),
                null, CodeRefIds.AUTH_CONVERTER);
    }

    private TraceStep authenticationEstablished(RawTrace raw) {
        RawTrace.AuthInfo auth = raw.authentication();
        if (raw.handlerInvoked() && auth != null) {
            return step("authentication-established", "Authentication is stored in the SecurityContext",
                    List.of("securityContext"), "converter", "securityContext", CAPTURED,
                    "The caller is now authenticated for this request only. The authorities below were built from the "
                            + "database, whatever the token's role claim says. The context is cleared when the request ends "
                            + "because the application is stateless.",
                    List.of(ev("Authenticated as", auth.name()),
                            ev("Authorities (from the database)", auth.authorities().isEmpty() ? "(none)"
                                    : String.join(", ", auth.authorities()))),
                    null, CodeRefIds.AUTH_CONVERTER);
        }
        return step("authentication-established", "Authentication was established (inferred)", List.of("securityContext"),
                "converter", "securityContext", DERIVED,
                "No controller was reached, so the security context was not observed. It is inferred that the caller was "
                        + "authenticated: the token was accepted, the database lookup succeeded, and the response is not a 401.",
                List.of(ev("Observed status", String.valueOf(raw.status()))), null, CodeRefIds.AUTH_CONVERTER);
    }

    private TraceStep authorizationDecision(RawTrace raw) {
        if (raw.handlerInvoked()) {
            return step("authorization-decision", "Authorization allows the request", List.of("authorization"),
                    "securityContext", "authorization", CAPTURED,
                    "AuthorizationFilter compared the caller's database-derived authorities with the rule for this path "
                            + "and let the request through to the controller.",
                    List.of(ev("Controller reached", "yes")), null, CodeRefIds.LAB_SECURE_CHAIN);
        }
        return step("authorization-decision", "Authorization denies the request (inferred)", List.of("authorization"),
                "securityContext", "authorization", DERIVED,
                "The caller is authenticated but the stored role does not satisfy the rule for this path, so "
                        + "AuthorizationFilter refuses it. Inferred from an authenticated caller (database lookup, no "
                        + "invalid_token challenge) receiving a 403 without a controller being reached.",
                List.of(ev("Observed status", String.valueOf(raw.status())), ev("Controller reached", "no")),
                null, CodeRefIds.LAB_SECURE_CHAIN);
    }

    private TraceStep controllerReached(RawTrace raw) {
        RawTrace.HandlerInfo h = raw.handler();
        return step("controller-reached", "The controller runs", List.of("controller"), "authorization", "controller",
                CAPTURED,
                "The request passed authentication and authorization and DispatcherServlet invoked the controller method.",
                List.of(ev("Selected handler", h.controller() + "#" + h.method()),
                        ev("Observed status", String.valueOf(raw.status()))),
                null, CodeRefIds.forMethod(h.controller(), h.method()));
    }

    /** anonymous = the refusal came from the authorization rule (a drawable edge); otherwise the token was refused earlier. */
    private TraceStep outcome401(RawTrace raw, boolean anonymous) {
        return step("response-401", "Response: 401 Unauthorized", List.of("entryPoint"),
                anonymous ? "authorization" : null, anonymous ? "entryPoint" : null, CAPTURED,
                "The request ended with a 401 before any controller ran. 401 means 'not authenticated': the caller could "
                        + "not be identified from valid credentials.",
                List.of(ev("Status", String.valueOf(raw.status())), ev("Content type", String.valueOf(raw.contentType())),
                        ev("WWW-Authenticate", orNone(raw.wwwAuthenticate())), ev("Controller reached", "no"),
                        ev("SQL statements prepared", String.valueOf(raw.sql().size()))),
                null, null);
    }

    private TraceStep mechanism401(RawTrace raw) {
        return step("mechanism-401", "Which component wrote the 401", List.of("entryPoint"), null, null, DERIVED,
                "Inferred: Spring Security's ExceptionTranslationFilter called the AuthenticationEntryPoint, which in SpringLab "
                        + "(RestAuthenticationEntryPoint) delegates to BearerTokenAuthenticationEntryPoint for the "
                        + "WWW-Authenticate header and writes an application/problem+json body. The attribution comes from the "
                        + "observed status, content type and Bearer challenge, not from direct observation.",
                List.of(ev("Observed content type", String.valueOf(raw.contentType()))), null, CodeRefIds.ENTRY_POINT);
    }

    private TraceStep outcome403(RawTrace raw) {
        return step("response-403", "Response: 403 Forbidden", List.of("accessDenied"), "authorization", "accessDenied",
                CAPTURED,
                "The request ended with a 403 before any controller ran. 403 means 'authenticated, but not allowed': the "
                        + "caller is known and the answer is still no.",
                List.of(ev("Status", String.valueOf(raw.status())), ev("Content type", String.valueOf(raw.contentType())),
                        ev("Controller reached", "no"), ev("SQL statements prepared", String.valueOf(raw.sql().size()))),
                null, null);
    }

    private TraceStep mechanism403(RawTrace raw) {
        return step("mechanism-403", "Which component wrote the 403", List.of("accessDenied"), null, null, DERIVED,
                "Inferred: ExceptionTranslationFilter caught the access-denial and called the AccessDeniedHandler, which in "
                        + "SpringLab (RestAccessDeniedHandler) writes an application/problem+json 403. The attribution comes "
                        + "from the observed status and content type, not from direct observation.",
                List.of(ev("Observed content type", String.valueOf(raw.contentType()))), null,
                CodeRefIds.ACCESS_DENIED_HANDLER);
    }

    private TraceStep unclassified(RawTrace raw) {
        return step("response", "Response", List.of("client"), null, null, CAPTURED,
                "The request ended with this response. The evidence did not allow the outcome to be classified further.",
                List.of(ev("Status", String.valueOf(raw.status())), ev("WWW-Authenticate", orNone(raw.wwwAuthenticate()))),
                null, null);
    }

    // ------------------------------------------------------------------ helpers

    private static TraceStep step(String id, String title, List<String> nodes, String edgeFrom, String edgeTo,
                                  Provenance provenance, String explanation, List<Evidence> evidence,
                                  List<SqlStatement> sql, String codeRefId) {
        return new TraceStep(id, title, nodes, edgeFrom, edgeTo, "REQUEST", provenance, explanation, evidence,
                sql == null ? List.of() : sql, codeRefId);
    }

    private static Evidence ev(String label, String value) {
        return new Evidence(label, value);
    }

    private static String orNone(String value) {
        return value == null ? "(none)" : value;
    }

    static String group(Pattern pattern, String text) {
        if (text == null) {
            return null;
        }
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1) : null;
    }
}
