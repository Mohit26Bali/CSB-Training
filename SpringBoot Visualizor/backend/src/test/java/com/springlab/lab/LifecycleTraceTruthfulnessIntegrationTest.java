package com.springlab.lab;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.catalog.LifecycleCatalog;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.LifecycleTrace.SqlStatement;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import com.springlab.lab.trace.LifecycleTraceFilter;
import com.springlab.lab.trace.Provenance;
import com.springlab.lab.trace.TraceStore;
import com.springlab.service.TopicService;
import com.springlab.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every claim the REST Request Lifecycle visualization makes is executed here against the real application and MySQL.
 * The SQL count reported by the trace is cross-checked against Hibernate's OWN statistics counter, which is
 * independent of the StatementInspector the trace relies on.
 */
class LifecycleTraceTruthfulnessIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private LifecycleCatalog catalog;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private FilterChainProxy filterChainProxy;
    @Autowired
    private ObjectMapper objectMapper;

    /** What came back from one traced call, plus the independent measurement. */
    private record Observed(int httpStatus, LifecycleTrace trace, long hibernatePreparedStatements, String traceId) {
    }

    private Observed call(User owner, String authorization, String url) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        MockHttpServletRequestBuilder request = get(url).header(LifecycleTraceFilter.REQUEST_HEADER, traceTicket(owner));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        long before = stats.getPrepareStatementCount();
        MvcResult result = mockMvc.perform(request).andReturn();
        long prepared = stats.getPrepareStatementCount() - before;
        String id = result.getResponse().getHeader(LifecycleTraceFilter.RESPONSE_HEADER);
        assertThat(id).as("trace id header for %s", url).isNotNull();
        LifecycleTrace trace = traceStore.findOwned(id, owner.getId()).orElseThrow();
        return new Observed(result.getResponse().getStatus(), trace, prepared, id);
    }

    private static String tamper(String bearerHeader) {
        String[] parts = bearerHeader.split("\\.");
        char first = parts[2].charAt(0);
        parts[2] = (first == 'A' ? 'B' : 'A') + parts[2].substring(1); // changes the first 6 bits of the signature
        return String.join(".", parts);
    }

    private String authFor(String variant, User user) {
        return switch (variant) {
            case "NO_TOKEN" -> null;
            case "TAMPERED_TOKEN" -> tamper(bearer(user));
            default -> bearer(user);
        };
    }

    private void assertMatches(String what, LifecycleCatalog.Expectation expected, Observed observed) {
        LifecycleTrace trace = observed.trace();
        assertThat(observed.httpStatus()).as("%s: HTTP status seen by the client", what).isEqualTo(trace.status());
        // The independent oracle: the trace's SQL count must equal what Hibernate itself prepared.
        assertThat(trace.sqlCount()).as("%s: captured SQL count vs Hibernate statistics", what)
                .isEqualTo((int) observed.hibernatePreparedStatements());
        if (expected.status() != null) {
            assertThat(trace.status()).as("%s: status", what).isEqualTo(expected.status());
        }
        if (expected.handlerInvoked() != null) {
            assertThat(trace.handlerInvoked()).as("%s: controller invoked", what).isEqualTo(expected.handlerInvoked());
        }
        if (expected.sqlCount() != null) {
            assertThat(trace.sqlCount()).as("%s: SQL statements", what).isEqualTo(expected.sqlCount());
        }
        assertProvenanceInvariants(what, trace);
    }

    private void assertProvenanceInvariants(String what, LifecycleTrace trace) {
        for (TraceStep step : trace.steps()) {
            if (!step.sql().isEmpty()) {
                assertThat(step.provenance()).as("%s: step %s has SQL", what, step.id()).isEqualTo(Provenance.CAPTURED);
            }
            if (step.id().startsWith("database-")) {
                assertThat(step.provenance()).as("%s: database execution is never observed", what)
                        .isEqualTo(Provenance.CONCEPTUAL);
            }
        }
        assertThat(trace.sqlCount()).isEqualTo(trace.steps().stream().mapToInt(s -> s.sql().size()).sum());
    }

    // ------------------------------------------------------------------ every catalogued claim, every role

    @Test
    void everyCatalogTargetBehavesAsDocumentedForEveryRole() throws Exception {
        for (Role role : Role.values()) {
            User user = createUser("u_" + role.name().toLowerCase(), role);
            for (LifecycleCatalog.Target target : catalog.catalog().targets()) {
                // Targets are documented for a normal, valid-token call.
                Observed observed = call(user, bearer(user), target.url());
                LifecycleCatalog.expectationFor(target.expectation(), role.name())
                        .ifPresentOrElse(expected -> assertMatches(role + " -> " + target.id(), expected, observed),
                                () -> { throw new AssertionError("no expectation for " + role + " on " + target.id()); });
            }
        }
    }

    @Test
    void everyLiveExperimentBehavesAsClaimedForEveryRole() throws Exception {
        for (Role role : Role.values()) {
            User user = createUser("x_" + role.name().toLowerCase(), role);
            for (LifecycleCatalog.Experiment experiment : catalog.catalog().experiments()) {
                if (!experiment.kind().equals("LIVE")) {
                    continue;
                }
                LifecycleCatalog.Target target = catalog.target(experiment.target()).orElseThrow();
                Observed observed = call(user, authFor(experiment.variant(), user), target.url());
                LifecycleCatalog.Expectation expected =
                        LifecycleCatalog.expectationFor(experiment.expectation(), role.name()).orElseThrow();
                assertMatches(role + " / " + experiment.id(), expected, observed);
            }
        }
    }

    // ------------------------------------------------------------------ detailed claims of individual scenarios

    @Test
    void meEndpointShowsAuthenticationLookupBeforeTheControllerAndRequestLookupAfter() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        Observed observed = call(sam, bearer(sam), "/api/auth/me");
        LifecycleTrace trace = observed.trace();

        assertThat(trace.status()).isEqualTo(200);
        assertThat(trace.sql()).hasSize(2);
        assertThat(trace.sql().get(0).phase()).isEqualTo("SECURITY");
        assertThat(trace.sql().get(0).trigger()).isEqualTo("UserRepository.findAuthInfoByUsername");
        assertThat(trace.sql().get(1).phase()).isEqualTo("HANDLER");
        assertThat(trace.sql().get(1).trigger()).isEqualTo("UserRepository.findProfileByUsername");
        assertThat(trace.sql()).allSatisfy(s -> assertThat(s.sql()).containsIgnoringCase("from users").contains("?"));
        // Placeholders only: the bound username must never be present in the captured text.
        assertThat(trace.sql()).noneMatch(s -> s.sql().contains("sam"));
        assertThat(ids(trace)).containsSubsequence("client-request", "security-filter-chain", "auth-lookup-0", "sql-0",
                "database-0", "dispatch", "controller", "service-1", "repository-2", "sql-2", "database-2",
                "serialization", "response");
        TraceStep dispatch = step(trace, "dispatch");
        assertThat(evidence(dispatch, "Selected handler")).isEqualTo("AuthController#me");
        assertThat(evidence(dispatch, "Matched mapping")).isEqualTo("/api/auth/me");
        assertThat(evidence(dispatch, "Authenticated as")).isEqualTo("sam");
        assertThat(evidence(dispatch, "Authorities")).isEqualTo("ROLE_STUDENT");
    }

    @Test
    void topicsEndpointRunsOneQueryWithACountSubselectAndTheDeclaredTransactionIsReal() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        LifecycleTrace trace = call(sam, bearer(sam), "/api/topics").trace();

        assertThat(trace.sql()).hasSize(2);
        SqlStatement topicsQuery = trace.sql().get(1);
        assertThat(topicsQuery.sql()).containsIgnoringCase("from topics").containsIgnoringCase("count(");
        assertThat(topicsQuery.trigger()).isEqualTo("TopicRepository.findAllWithActiveQuestionCount");
        // "@Transactional on this method" is read from the annotation: verify against independent reflection.
        var annotation = TopicService.class.getMethod("list").getAnnotation(Transactional.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.readOnly()).isTrue();
        assertThat(evidence(step(trace, "service-1"), "@Transactional on this method"))
                .isEqualTo("declared, readOnly = true");
    }

    @Test
    void securityFilterListEqualsTheChainSpringSecurityReportsForThatUrl() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        LifecycleTrace trace = call(sam, bearer(sam), "/api/auth/me").trace();

        List<String> expected = filterChainProxy.getFilters("/api/auth/me").stream()
                .map(f -> f.getClass().getSimpleName()).toList();
        String shown = evidence(step(trace, "security-filter-chain"), "Configured filters (" + expected.size() + ")");
        assertThat(shown).isEqualTo(String.join(" → ", expected));
        assertThat(expected).contains("BearerTokenAuthenticationFilter", "ExceptionTranslationFilter",
                "AuthorizationFilter");
    }

    @Test
    void missingChallengeReachesApplicationCodeAndIsTranslatedTo404() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        Observed observed = call(sam, bearer(sam), "/api/challenges/0");
        LifecycleTrace trace = observed.trace();

        assertThat(trace.status()).isEqualTo(404);
        assertThat(trace.contentType()).contains("problem+json");
        assertThat(trace.handlerInvoked()).isTrue();
        assertThat(evidence(step(trace, "dispatch"), "Selected handler")).isEqualTo("ChallengeController#get");
        TraceStep service = trace.steps().stream().filter(s -> s.id().startsWith("service-")).findFirst().orElseThrow();
        assertThat(evidence(service, "Threw")).isEqualTo("ResourceNotFoundException");
        TraceStep handling = step(trace, "exception-handling");
        assertThat(handling.provenance()).isEqualTo(Provenance.DERIVED); // the advice method itself is not observed
        assertThat(ids(trace)).doesNotContain("serialization");
    }

    @Test
    void anonymousAndTamperedRequestsNeverReachApplicationCodeOrTheDatabase() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        for (String authorization : new String[]{null, tamper(bearer(sam)), "Bearer not.a.jwt"}) {
            Observed observed = call(sam, authorization, "/api/auth/me");
            LifecycleTrace trace = observed.trace();
            assertThat(trace.status()).isEqualTo(401);
            assertThat(trace.handlerInvoked()).isFalse();
            assertThat(trace.sqlCount()).isZero();
            assertThat(observed.hibernatePreparedStatements()).isZero();
            assertThat(ids(trace)).containsExactly("client-request", "security-filter-chain",
                    "rejected-before-controller", "rejection-mechanism", "response");
            assertThat(step(trace, "rejection-mechanism").provenance()).isEqualTo(Provenance.DERIVED);
        }
    }

    @Test
    void forbiddenRequestShowsOnlyTheRoleLookupAndNoController() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        LifecycleTrace trace = call(sam, bearer(sam), "/api/admin/users?size=1").trace();

        assertThat(trace.status()).isEqualTo(403);
        assertThat(trace.handlerInvoked()).isFalse();
        assertThat(trace.sql()).hasSize(1);
        assertThat(trace.sql().get(0).phase()).isEqualTo("SECURITY");
        assertThat(ids(trace)).contains("auth-lookup-0", "rejected-before-controller")
                .doesNotContain("controller", "dispatch");
    }

    // ------------------------------------------------------------------ the trace endpoint and what it must never leak

    @Test
    void tracesAreReadableOnlyWithAuthenticationAndNeverContainSecrets() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String authorization = bearer(sam);
        String id = call(sam, authorization, "/api/auth/me").traceId();

        mockMvc.perform(get("/api/lab/traces/" + id)).andExpect(status().isUnauthorized());
        MvcResult result = mockMvc.perform(get("/api/lab/traces/" + id).header("Authorization", authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("LIVE"))
                .andExpect(jsonPath("$.id").value(id))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String rawToken = authorization.substring("Bearer ".length());
        assertThat(json).doesNotContain(rawToken).doesNotContain("Bearer ").doesNotContain("eyJ")
                .doesNotContain("{bcrypt}").doesNotContain("passwordHash");
        // Hibernate's SELECT for the full User entity legitimately names the password_hash COLUMN (a schema name, not
        // a value). Apart from that column name the word must not appear anywhere, and no hash value may.
        assertThat(json.replace("password_hash", "")).doesNotContain("password");
        assertThat(json).doesNotContain("$2a$").doesNotContain("$2b$");
        // The user's own name appears (as the authenticated principal) but never their e-mail address.
        assertThat(json).doesNotContain("sam@example.com");
    }

    @Test
    void unknownOrMalformedTraceIdsAreNotFound() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String auth = bearer(sam);

        mockMvc.perform(get("/api/lab/traces/00000000-0000-4000-8000-000000000000").header("Authorization", auth))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/lab/traces/not-a-uuid").header("Authorization", auth))
                .andExpect(status().isNotFound());
    }

    private String traceIdOf(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request).andReturn().getResponse().getHeader(LifecycleTraceFilter.RESPONSE_HEADER);
    }

    @Test
    void tracingIsIgnoredUnlessTheRequestIsACataloguedGetWithAValidTicket() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String auth = bearer(sam);
        String ticket = traceTicket(sam);

        // Not in the catalogue (the ticket is not consumed by such a request).
        assertThat(traceIdOf(get("/api/progress/me").header("Authorization", auth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, ticket))).isNull();
        // Catalogued path but not GET.
        assertThat(traceIdOf(post("/api/topics").header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                .content("{}").header(LifecycleTraceFilter.REQUEST_HEADER, ticket))).isNull();
        // No header, or a value that is not a ticket: the old literal "1", arbitrary text, an unknown UUID.
        assertThat(traceIdOf(get("/api/topics").header("Authorization", auth))).isNull();
        for (String bogus : List.of("1", "true", "garbage", java.util.UUID.randomUUID().toString())) {
            assertThat(traceIdOf(get("/api/topics").header("Authorization", auth)
                    .header(LifecycleTraceFilter.REQUEST_HEADER, bogus))).as("header value %s", bogus).isNull();
        }
        // The ticket survived all of that, works once, and is then spent.
        assertThat(traceIdOf(get("/api/topics").header("Authorization", auth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, ticket))).isNotNull();
        assertThat(traceIdOf(get("/api/topics").header("Authorization", auth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, ticket))).isNull();
    }

    @Test
    void tracedRequestsReturnTheSameBodyAsUntracedOnes() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String auth = bearer(sam);

        String plain = mockMvc.perform(get("/api/auth/me").header("Authorization", auth)).andReturn().getResponse()
                .getContentAsString();
        String traced = mockMvc.perform(get("/api/auth/me").header("Authorization", auth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, traceTicket(sam))).andReturn().getResponse()
                .getContentAsString();

        assertThat(objectMapper.readTree(traced)).isEqualTo(objectMapper.readTree(plain));
    }

    // ------------------------------------------------------------------ helpers

    private static List<String> ids(LifecycleTrace trace) {
        return trace.steps().stream().map(TraceStep::id).toList();
    }

    private static TraceStep step(LifecycleTrace trace, String id) {
        return trace.steps().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no step " + id + " in " + ids(trace)));
    }

    private static String evidence(TraceStep step, String label) {
        Map<String, String> values = new java.util.LinkedHashMap<>();
        step.evidence().forEach(e -> values.put(e.label(), e.value()));
        assertThat(values).as("evidence of step %s", step.id()).containsKey(label);
        return values.get(label);
    }
}
