package com.springlab.lab.trace;

import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure unit tests of the rules that decide what the JWT simulator may claim about a request. */
class JwtStepAssemblerTest {

    static final Set<String> NODES = Set.of("client", "bearerFilter", "authManager", "jwtDecoder", "converter", "database",
            "securityContext", "authorization", "controller", "entryPoint", "accessDenied");
    static final Set<String> EDGES = Set.of("client->bearerFilter", "bearerFilter->authManager", "authManager->jwtDecoder",
            "jwtDecoder->converter", "converter->database", "converter->securityContext",
            "securityContext->authorization", "authorization->controller", "authorization->accessDenied",
            "authorization->entryPoint");
    static final List<String> FILTERS = List.of("SecurityContextHolderFilter", "BearerTokenAuthenticationFilter",
            "ExceptionTranslationFilter", "AuthorizationFilter");
    static final String EXPIRED = "Bearer error=\"invalid_token\", error_description=\"An error occurred while attempting "
            + "to decode the Jwt: Jwt expired at 2026-01-01T00:00:00Z\", error_uri=\"https://tools.ietf.org/html/rfc6750#section-3.1\"";

    private final JwtStepAssembler assembler = new JwtStepAssembler();

    private static RawTrace.CallEvent lookup() {
        return new RawTrace.CallEvent(0, Layer.REPOSITORY, "UserRepository", "findAuthInfoByUsername", Phase.SECURITY,
                1000, 4000, null, null, 0);
    }

    private static RawTrace.SqlEvent lookupSql() {
        return new RawTrace.SqlEvent("select u1_0.username,u1_0.role,u1_0.enabled from users u1_0 where u1_0.username=?",
                Phase.SECURITY, 0, "UserRepository.findAuthInfoByUsername", 2000);
    }

    private static RawTrace raw(String scheme, boolean lookedUp, boolean handler, int status, String challenge) {
        return new RawTrace("GET", "/api/lab/secure/admin-area", scheme != null, FILTERS,
                handler ? new RawTrace.HandlerInfo("LabSecureController", "adminArea", "/api/lab/secure/admin-area") : null,
                handler ? new RawTrace.AuthInfo("sam", List.of("ROLE_ADMIN")) : null,
                lookedUp ? List.of(lookup()) : List.of(), lookedUp ? List.of(lookupSql()) : List.of(), status,
                status == 200 ? "application/json" : "application/problem+json", 5_000_000, scheme, challenge);
    }

    private static List<String> ids(LifecycleTrace trace) {
        return trace.steps().stream().map(TraceStep::id).toList();
    }

    private static TraceStep step(LifecycleTrace trace, String id) {
        return trace.steps().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no step " + id + " in " + ids(trace)));
    }

    private LifecycleTrace validRequest() {
        return assembler.assemble("t", raw("Bearer", true, true, 200, null));
    }

    private LifecycleTrace expiredToken() {
        return assembler.assemble("t", raw("Bearer", false, false, 401, EXPIRED));
    }

    private LifecycleTrace forbidden() {
        return assembler.assemble("t", raw("Bearer", true, false, 403, null));
    }

    private LifecycleTrace noToken() {
        return assembler.assemble("t", raw(null, false, false, 401, "Bearer"));
    }

    private LifecycleTrace disabledAccount() {
        return assembler.assemble("t", raw("Bearer", true, false, 401,
                "Bearer error=\"invalid_token\", error_description=\"Token is not valid for an active account\""));
    }

    // ------------------------------------------------------------------ paths

    @Test
    void validTokenWalksThroughEveryStageToTheController() {
        LifecycleTrace trace = validRequest();

        assertThat(ids(trace)).containsExactly("client-request", "security-chain", "token-validation", "database-lookup",
                "authentication-established", "authorization-decision", "controller-reached");
        assertThat(trace.sqlCount()).isEqualTo(1);
        assertThat(step(trace, "database-lookup").sql()).hasSize(1);
        assertThat(step(trace, "database-lookup").nodes()).containsExactly("converter", "database");
    }

    @Test
    void anExpiredTokenStopsAtTheDecoderWithNoDatabaseLookup() {
        LifecycleTrace trace = expiredToken();

        assertThat(ids(trace)).containsExactly("client-request", "security-chain", "token-validation", "response-401",
                "mechanism-401");
        assertThat(trace.sqlCount()).isZero();
        TraceStep validation = step(trace, "token-validation");
        assertThat(validation.title()).contains("rejects");
        assertThat(validation.evidence()).anyMatch(e -> e.label().equals("Reason given by the framework")
                && e.value().contains("Jwt expired"));
        // The decoder rejects the token before authorization runs, so there is no edge to draw.
        assertThat(step(trace, "response-401").edgeFrom()).isNull();
    }

    @Test
    void aForbiddenRequestIsAuthenticatedButDeniedAndNeverReachesTheController() {
        LifecycleTrace trace = forbidden();

        assertThat(ids(trace)).containsExactly("client-request", "security-chain", "token-validation", "database-lookup",
                "authentication-established", "authorization-decision", "response-403", "mechanism-403");
        assertThat(step(trace, "token-validation").title()).contains("accepts");
        assertThat(trace.handlerInvoked()).isFalse();
        assertThat(ids(trace)).doesNotContain("controller-reached");
    }

    @Test
    void noTokenIsAnAnonymousRequestRefusedWith401BeforeAnyDecoding() {
        LifecycleTrace trace = noToken();

        assertThat(ids(trace)).containsExactly("client-request", "security-chain", "no-bearer-token", "anonymous-denied",
                "response-401", "mechanism-401");
        assertThat(ids(trace)).doesNotContain("token-validation", "database-lookup");
        assertThat(trace.sqlCount()).isZero();
        assertThat(step(trace, "response-401").edgeFrom() + "->" + step(trace, "response-401").edgeTo())
                .isEqualTo("authorization->entryPoint");
    }

    @Test
    void aValidTokenForADisabledAccountIsRefusedAfterTheLookupNotByTheDecoder() {
        LifecycleTrace trace = disabledAccount();

        assertThat(ids(trace)).containsExactly("client-request", "security-chain", "token-validation", "database-lookup",
                "rejected-after-lookup", "response-401", "mechanism-401");
        assertThat(step(trace, "token-validation").title()).contains("accepts"); // the decoder passed it
        assertThat(step(trace, "rejected-after-lookup").provenance()).isEqualTo(Provenance.DERIVED);
    }

    // ------------------------------------------------------------------ honesty of the provenance

    @Test
    void whatWasObservedIsCapturedAndWhatWasInferredIsNot() {
        for (LifecycleTrace trace : List.of(validRequest(), expiredToken(), forbidden(), noToken(), disabledAccount())) {
            assertThat(step(trace, "client-request").provenance()).isEqualTo(Provenance.CAPTURED);
            assertThat(step(trace, "security-chain").provenance()).isEqualTo(Provenance.CAPTURED);
            // Which component rejected the request is an inference in every case.
            trace.steps().stream().filter(s -> s.id().startsWith("mechanism-") || s.id().equals("token-validation")
                    || s.id().equals("no-bearer-token") || s.id().equals("anonymous-denied"))
                    .forEach(s -> assertThat(s.provenance()).as(s.id()).isEqualTo(Provenance.DERIVED));
            trace.steps().stream().filter(s -> s.id().startsWith("response-"))
                    .forEach(s -> assertThat(s.provenance()).as(s.id()).isEqualTo(Provenance.CAPTURED));
        }
    }

    @Test
    void theSecurityContextIsCapturedOnlyWhenTheControllerObservedIt() {
        assertThat(step(validRequest(), "authentication-established").provenance()).isEqualTo(Provenance.CAPTURED);
        assertThat(step(validRequest(), "authorization-decision").provenance()).isEqualTo(Provenance.CAPTURED);
        // For a 403 nothing observed the SecurityContext, so the same steps must be INFERRED.
        assertThat(step(forbidden(), "authentication-established").provenance()).isEqualTo(Provenance.DERIVED);
        assertThat(step(forbidden(), "authorization-decision").provenance()).isEqualTo(Provenance.DERIVED);
    }

    @Test
    void authoritiesAreReportedAsComingFromTheDatabase() {
        TraceStep established = step(validRequest(), "authentication-established");

        assertThat(established.evidence()).anyMatch(e -> e.label().equals("Authorities (from the database)")
                && e.value().equals("ROLE_ADMIN"));
        assertThat(established.explanation()).contains("whatever the token's role claim says");
    }

    @Test
    void sqlTextAppearsOnlyOnCapturedSteps() {
        for (LifecycleTrace trace : List.of(validRequest(), expiredToken(), forbidden(), noToken(), disabledAccount())) {
            for (TraceStep s : trace.steps()) {
                if (!s.sql().isEmpty()) {
                    assertThat(s.provenance()).as(s.id()).isEqualTo(Provenance.CAPTURED);
                }
            }
            assertThat(trace.steps().stream().mapToInt(s -> s.sql().size()).sum()).isEqualTo(trace.sqlCount());
        }
    }

    @Test
    void theTokenAndItsValueNeverAppearInAnyStep() {
        for (LifecycleTrace trace : List.of(validRequest(), expiredToken(), noToken())) {
            String all = trace.steps().toString() + trace.limitations();
            assertThat(all).doesNotContain("eyJ").doesNotContain("Bearer eyJ");
            assertThat(step(trace, "client-request").evidence()).anyMatch(e -> e.label().equals("Token value")
                    && e.value().equals("never recorded"));
        }
    }

    @Test
    void everyStepUsesKnownNodesEdgesAndCodeReferences() {
        for (LifecycleTrace trace : List.of(validRequest(), expiredToken(), forbidden(), noToken(), disabledAccount())) {
            Set<String> seen = new HashSet<>();
            for (TraceStep s : trace.steps()) {
                assertThat(seen.add(s.id())).as("duplicate id %s", s.id()).isTrue();
                assertThat(NODES).containsAll(s.nodes());
                if (s.edgeFrom() != null && s.edgeTo() != null) {
                    assertThat(EDGES).contains(s.edgeFrom() + "->" + s.edgeTo());
                }
                if (s.codeRefId() != null) {
                    assertThat(CodeRefIds.ALL).contains(s.codeRefId());
                }
                assertThat(s.explanation()).isNotBlank();
            }
        }
    }

    @Test
    void limitationsAreDisclosedIncludingTheInferenceAboutWhoRejectedTheRequest() {
        LifecycleTrace trace = expiredToken();

        assertThat(trace.limitations()).anyMatch(l -> l.contains("inferred") && l.contains("WWW-Authenticate"));
        assertThat(trace.limitations()).anyMatch(l -> l.contains("placeholders"));
        assertThat(trace.limitations()).anyMatch(l -> l.contains("lab signing key"));
    }
}
