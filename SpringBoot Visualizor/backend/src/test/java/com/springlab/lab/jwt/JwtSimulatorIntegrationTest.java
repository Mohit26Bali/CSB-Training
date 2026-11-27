package com.springlab.lab.jwt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.jwt.JwtSimulatorCatalog.Experiment;
import com.springlab.lab.jwt.JwtSimulatorCatalog.Expectation;
import com.springlab.lab.trace.LifecycleTrace;
import com.springlab.lab.trace.LifecycleTrace.TraceStep;
import com.springlab.lab.trace.LifecycleTraceFilter;
import com.springlab.lab.trace.Provenance;
import com.springlab.lab.trace.TraceStore;
import com.springlab.support.AbstractIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import javax.crypto.SecretKey;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every behaviour the JWT Security Simulator claims is executed here through the real filter chains and MySQL. Tokens
 * are obtained from the real issue endpoint; the SQL count in each trace is cross-checked against Hibernate's own
 * statistics, which are independent of the trace's StatementInspector.
 */
class JwtSimulatorIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtSimulatorCatalog catalog;
    @Autowired
    private EntityManagerFactory entityManagerFactory;
    @Autowired
    private FilterChainProxy filterChainProxy;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private LabJwtService labJwtService;

    private record Observed(int httpStatus, String challenge, LifecycleTrace trace, long hibernatePrepared, String body,
                            String traceId) {
    }

    // ------------------------------------------------------------------ helpers

    private JsonNode issue(User user, String variant, String claimedRole) throws Exception {
        String body = claimedRole == null ? "{\"variant\":\"" + variant + "\"}"
                : "{\"variant\":\"" + variant + "\",\"claimedRole\":\"" + claimedRole + "\"}";
        MvcResult result = mockMvc.perform(post("/api/lab/jwt/issue").header("Authorization", bearer(user))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** One traced request to a lab endpoint with the given Authorization header value (null = none). */
    private Observed callLab(User owner, String path, String authorization) throws Exception {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.setStatisticsEnabled(true);
        MockHttpServletRequestBuilder request = get(path).header(LifecycleTraceFilter.REQUEST_HEADER, traceTicket(owner));
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        long before = stats.getPrepareStatementCount();
        MvcResult result = mockMvc.perform(request).andReturn();
        long prepared = stats.getPrepareStatementCount() - before;
        MockHttpServletResponse response = result.getResponse();
        String id = response.getHeader(LifecycleTraceFilter.RESPONSE_HEADER);
        assertThat(id).as("trace id for %s", path).isNotNull();
        return new Observed(response.getStatus(), response.getHeader("WWW-Authenticate"),
                traceStore.findOwned(id, owner.getId()).orElseThrow(), prepared, response.getContentAsString(), id);
    }

    private Observed callLabWithToken(User owner, String path, JsonNode issued) throws Exception {
        return callLab(owner, path, "Bearer " + issued.get("token").asText());
    }

    private static String stepIdsOf(LifecycleTrace trace) {
        return trace.steps().stream().map(TraceStep::id).toList().toString();
    }

    private static TraceStep step(LifecycleTrace trace, String id) {
        return trace.steps().stream().filter(s -> s.id().equals(id)).findFirst()
                .orElseThrow(() -> new AssertionError("no step " + id + " in " + stepIdsOf(trace)));
    }

    private String pathOf(String targetId) {
        return catalog.target(targetId).orElseThrow().path();
    }

    private void assertProvenance(String what, LifecycleTrace trace) {
        for (TraceStep s : trace.steps()) {
            if (!s.sql().isEmpty()) {
                assertThat(s.provenance()).as("%s: step %s carries SQL", what, s.id()).isEqualTo(Provenance.CAPTURED);
            }
            if (s.id().equals("token-validation") || s.id().startsWith("mechanism-")) {
                assertThat(s.provenance()).as("%s: %s is an inference", what, s.id()).isEqualTo(Provenance.DERIVED);
            }
        }
        assertThat(trace.steps().stream().mapToInt(s -> s.sql().size()).sum()).isEqualTo(trace.sqlCount());
    }

    // ------------------------------------------------------------------ every LIVE experiment, every role

    @Test
    void everyLiveExperimentBehavesAsTheLessonClaimsForEveryRole() throws Exception {
        for (Role role : Role.values()) {
            User user = createUser("lab_" + role.name().toLowerCase(), role);
            for (Experiment experiment : catalog.catalog().experiments()) {
                if (!experiment.kind().equals("LIVE")) {
                    continue;
                }
                String label = role + " / " + experiment.id();
                String authorization = null;
                JsonNode issued = null;
                if (!experiment.variant().equals("NO_TOKEN")) {
                    issued = issue(user, experiment.variant(), experiment.claimedRole());
                    authorization = "Bearer " + issued.get("token").asText();
                }
                Observed observed = callLab(user, pathOf(experiment.target()), authorization);
                Expectation expected = experiment.expectation().containsKey(role.name())
                        ? experiment.expectation().get(role.name()) : experiment.expectation().get("*");
                LifecycleTrace trace = observed.trace();

                assertThat(observed.httpStatus()).as("%s: HTTP status", label).isEqualTo(expected.status());
                assertThat(trace.status()).as("%s: traced status", label).isEqualTo(expected.status());
                assertThat(trace.handlerInvoked()).as("%s: controller reached", label).isEqualTo(expected.handlerInvoked());
                assertThat(trace.sqlCount()).as("%s: SQL statements", label).isEqualTo(expected.sqlCount());
                // The independent oracle: what Hibernate itself says it prepared.
                assertThat((long) trace.sqlCount()).as("%s: trace vs Hibernate statistics", label)
                        .isEqualTo(observed.hibernatePrepared());
                if (expected.challengeContains() != null) {
                    assertThat(observed.challenge()).as("%s: server's WWW-Authenticate", label)
                            .containsIgnoringCase(expected.challengeContains()).contains("invalid_token");
                }
                if (expected.status() == 401) {
                    assertThat(observed.challenge()).as("%s: 401 carries a Bearer challenge", label).startsWith("Bearer");
                }
                assertProvenance(label, trace);
                if (issued != null) {
                    assertThat(objectMapper.writeValueAsString(trace)).as("%s: token never in the trace", label)
                            .doesNotContain(issued.get("token").asText());
                }
            }
        }
    }

    // ------------------------------------------------------------------ the simulator's decoder vs the inspector

    @Test
    void theInspectorsVerdictForEveryIssuedTokenMatchesWhatTheRealFilterChainDoes() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        for (JwtSimulatorCatalog.Variant variant : catalog.catalog().variants()) {
            if (!variant.issued()) {
                continue;
            }
            JsonNode issued = issue(sam, variant.id(), null);
            Observed observed = callLabWithToken(sam, pathOf("whoami"), issued);
            boolean accepted = observed.httpStatus() == 200;

            assertThat(accepted).as("%s accepted by the chain", variant.id()).isEqualTo(variant.id().equals("VALID"));
            assertThat(issued.get("inspection").get("verification").get("valid").asBoolean())
                    .as("%s: inspector agrees with the chain", variant.id()).isEqualTo(accepted);
            assertThat(issued.get("inspection").get("stagesAgreeWithDecoder").asBoolean()).isTrue();
            // The inspector's separate endpoint gives the same answer for the same token.
            MvcResult inspected = mockMvc.perform(post("/api/lab/jwt/inspect").header("Authorization", bearer(sam))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"token\":\"" + issued.get("token").asText() + "\"}")).andExpect(status().isOk()).andReturn();
            JsonNode inspection = objectMapper.readTree(inspected.getResponse().getContentAsString());
            String firstFailed = null;
            for (JsonNode stage : inspection.get("stages")) {
                if (stage.get("status").asText().equals("FAILED")) {
                    firstFailed = stage.get("id").asText();
                    break;
                }
            }
            assertThat(firstFailed).as("%s: failing check", variant.id()).isEqualTo(variant.failingStage());
        }
    }

    @Test
    void theWhoAmIEndpointShowsDatabaseAuthoritiesNextToTheClaimThatDiffers() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        JsonNode issued = issue(sam, "VALID", "ADMIN");

        Observed observed = callLabWithToken(sam, pathOf("whoami"), issued);
        JsonNode who = objectMapper.readTree(observed.body());

        assertThat(observed.httpStatus()).isEqualTo(200);
        assertThat(who.get("username").asText()).isEqualTo("sam");
        assertThat(who.get("tokenRoleClaim").asText()).isEqualTo("ADMIN");
        assertThat(who.get("authoritiesFromDatabase")).extracting(JsonNode::asText).containsExactly("ROLE_STUDENT");
        assertThat(who.get("roleClaimMatchesDatabase").asBoolean()).isFalse();
        // The same facts, captured by the interceptor while the controller ran.
        assertThat(step(observed.trace(), "authentication-established").evidence())
                .anyMatch(e -> e.label().equals("Authorities (from the database)") && e.value().equals("ROLE_STUDENT"));
    }

    @Test
    void anAccountDisabledAfterTheTokenWasIssuedIsRefusedAfterTheLookupNotByTheDecoder() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        JsonNode issued = issue(sam, "VALID", null);
        sam.setEnabled(false);
        userRepository.saveAndFlush(sam);
        User watcher = createUser("watcher", Role.STUDENT); // sam can no longer authenticate, so cannot own a trace

        Observed observed = callLabWithToken(watcher, pathOf("whoami"), issued);

        assertThat(observed.httpStatus()).isEqualTo(401);
        assertThat(observed.challenge()).contains("invalid_token").contains("not valid for an active account");
        assertThat(observed.trace().sqlCount()).isEqualTo(1); // the lookup DID happen, unlike a decoder rejection
        assertThat(observed.hibernatePrepared()).isEqualTo(1);
        assertThat(step(observed.trace(), "token-validation").title()).contains("accepts");
        assertThat(step(observed.trace(), "rejected-after-lookup").provenance()).isEqualTo(Provenance.DERIVED);
    }

    @Test
    void aValidTokenForAnUnknownUserIsRefusedTheSameWay() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        JsonNode issued = issue(sam, "VALID", null);
        User watcher = createUser("watcher", Role.STUDENT); // the deleted user cannot own a trace
        userRepository.delete(sam);
        userRepository.flush();

        Observed observed = callLabWithToken(watcher, pathOf("whoami"), issued);

        assertThat(observed.httpStatus()).isEqualTo(401);
        assertThat(observed.challenge()).contains("not valid for an active account");
    }

    // ------------------------------------------------------------------ the lab key is separate and real tokens stay out

    @Test
    void aLabTokenIsUselessAgainstTheApplicationsRealEndpoints() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        JsonNode issued = issue(sam, "VALID", null);
        String labBearer = "Bearer " + issued.get("token").asText();

        for (String path : List.of("/api/auth/me", "/api/topics", "/api/progress/me")) {
            mockMvc.perform(get(path).header("Authorization", labBearer)).andExpect(status().isUnauthorized());
        }
        // And it cannot be used to reach the trace store or issue further tokens either.
        mockMvc.perform(post("/api/lab/jwt/issue").header("Authorization", labBearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"VALID\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aRealApplicationTokenIsRejectedByTheLabChain() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        Observed observed = callLab(sam, pathOf("whoami"), bearer(sam));

        assertThat(observed.httpStatus()).isEqualTo(401);
        assertThat(observed.challenge()).contains("invalid_token").containsIgnoringCase("signature");
        assertThat(observed.trace().sqlCount()).isZero();
    }

    @Test
    void noSecondJwtDecoderEncoderOrSecretKeyBeanExists() {
        assertThat(context.getBeanNamesForType(JwtDecoder.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(JwtEncoder.class)).hasSize(1);
        assertThat(context.getBeanNamesForType(SecretKey.class)).hasSize(1);
        assertThat(context.getBeansOfType(JwtDecoder.class).values()).doesNotContain(labJwtService.decoder());
        assertThat(context.getBean(JwtDecoder.class)).isNotSameAs(labJwtService.decoder());
    }

    @Test
    void theApplicationHasTheMainChainAndExactlyOneLabChainAndTheMainChainIsUnchanged() {
        var chains = filterChainProxy.getFilterChains();

        assertThat(chains).hasSize(2);
        assertThat(chains.get(0).toString()).contains("/api/lab/secure/**");
        for (String path : List.of("/api/auth/me", "/api/topics", "/api/admin/users", "/api/lab/meta", "/api/lab/traces/x")) {
            assertThat(filterChainProxy.getFilters(path)).as("chain for %s is the main chain", path)
                    .isEqualTo(chains.get(1).getFilters());
        }
        assertThat(filterChainProxy.getFilters("/api/lab/secure/whoami")).isEqualTo(chains.get(0).getFilters());
    }

    @Test
    void theLabKeyAppearsInNoResponseOrTrace() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        SecretKey labKey = (SecretKey) ReflectionTestUtils.getField(labJwtService, "labKey");
        byte[] key = labKey.getEncoded();
        List<String> forms = List.of(Base64.getEncoder().encodeToString(key),
                Base64.getUrlEncoder().withoutPadding().encodeToString(key), HexFormat.of().formatHex(key));

        JsonNode issued = issue(sam, "VALID", null);
        Observed traced = callLabWithToken(sam, pathOf("whoami"), issued);
        List<String> bodies = List.of(issued.toString(), objectMapper.writeValueAsString(traced.trace()), traced.body(),
                mockMvc.perform(get("/api/lab/jwt/components").header("Authorization", bearer(sam))).andReturn()
                        .getResponse().getContentAsString(),
                mockMvc.perform(get("/api/lab/jwt/catalog").header("Authorization", bearer(sam))).andReturn()
                        .getResponse().getContentAsString(),
                mockMvc.perform(get("/api/lab/meta").header("Authorization", bearer(sam))).andReturn()
                        .getResponse().getContentAsString());

        for (String body : bodies) {
            for (String form : forms) {
                assertThat(body).doesNotContain(form);
            }
        }
    }

    @Test
    void theRealTokenAndRealSecretNeverAppearInSimulatorResponsesOrTraces() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String realToken = bearer(sam).substring("Bearer ".length());
        com.springlab.config.JwtProperties props = context.getBean(com.springlab.config.JwtProperties.class);

        JsonNode issued = issue(sam, "VALID", null);
        Observed traced = callLabWithToken(sam, pathOf("whoami"), issued);
        MvcResult inspected = mockMvc.perform(post("/api/lab/jwt/inspect").header("Authorization", bearer(sam))
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + issued.get("token").asText() + "\"}"))
                .andReturn();
        String all = issued + objectMapper.writeValueAsString(traced.trace()) + inspected.getResponse().getContentAsString();

        assertThat(all).doesNotContain(realToken).doesNotContain(props.secret());
    }

    // ------------------------------------------------------------------ the token workshop endpoints

    @Test
    void issuingAndInspectingRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/lab/jwt/issue").contentType(MediaType.APPLICATION_JSON).content("{\"variant\":\"VALID\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/lab/jwt/inspect").contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"a.b.c\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lab/jwt/catalog")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/lab/jwt/components")).andExpect(status().isUnauthorized());
        // The lab-secure endpoints themselves answer 401 too, from the lab chain.
        mockMvc.perform(get("/api/lab/secure/whoami")).andExpect(status().isUnauthorized());
    }

    @Test
    void aLabTokenAlwaysNamesTheCallerAndNeverAnAttackerChosenSubject() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        createUser("root", Role.ADMIN);

        MvcResult result = mockMvc.perform(post("/api/lab/jwt/issue").header("Authorization", bearer(sam))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"variant\":\"VALID\",\"subject\":\"root\",\"sub\":\"root\",\"username\":\"root\"}"))
                .andExpect(status().isOk()).andReturn();
        JsonNode issued = objectMapper.readTree(result.getResponse().getContentAsString());
        JsonNode claims = issued.get("inspection").get("claims");

        assertThat(claims.get("sub").asText()).isEqualTo("sam");
        assertThat(issued.get("actualRole").asText()).isEqualTo("STUDENT");
        assertThat(issued.get("usableAt").asText()).contains("/api/lab/secure/");
    }

    @Test
    void issuingValidatesItsInput() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String auth = bearer(sam);
        for (String body : List.of("{}", "{\"variant\":\"NO_TOKEN\"}", "{\"variant\":\"BOGUS\"}",
                "{\"variant\":\"VALID\",\"claimedRole\":\"SUPERUSER\"}", "not json")) {
            mockMvc.perform(post("/api/lab/jwt/issue").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
    }

    @Test
    void inspectingValidatesItsInputAndCanReadAPastedRealTokenWithoutTrustingIt() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        String auth = bearer(sam);
        for (String body : List.of("{}", "{\"token\":\"\"}", "{\"token\":\"" + "x".repeat(4097) + "\"}")) {
            mockMvc.perform(post("/api/lab/jwt/inspect").header("Authorization", auth)
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        String real = auth.substring("Bearer ".length());
        MvcResult result = mockMvc.perform(post("/api/lab/jwt/inspect").header("Authorization", auth)
                .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + real + "\"}")).andExpect(status().isOk())
                .andReturn();
        JsonNode inspection = objectMapper.readTree(result.getResponse().getContentAsString());

        // Decoding works for any token (it is only Base64URL)...
        assertThat(inspection.get("claims").get("sub").asText()).isEqualTo("sam");
        // ...but a real application token is NOT valid for the lab decoder: it is signed with a different key.
        assertThat(inspection.get("verification").get("valid").asBoolean()).isFalse();
        assertThat(inspection.get("verification").get("category").asText()).isEqualTo("BAD_SIGNATURE");
        assertThat(inspection.get("signaturePreview").asText()).doesNotContain(real.split("\\.")[2]);
    }

    @Test
    void theComponentFactsMatchTheRunningApplication() throws Exception {
        User sam = createUser("sam", Role.STUDENT);

        MvcResult result = mockMvc.perform(get("/api/lab/jwt/components").header("Authorization", bearer(sam)))
                .andExpect(status().isOk()).andReturn();
        JsonNode facts = objectMapper.readTree(result.getResponse().getContentAsString());

        assertThat(facts.get("filterChains")).hasSize(2);
        assertThat(facts.get("filterChains").get(0).get("matcher").asText()).contains("/api/lab/secure/**");
        for (JsonNode chain : facts.get("filterChains")) {
            assertThat(chain.get("filters")).extracting(JsonNode::asText).contains("BearerTokenAuthenticationFilter",
                    "AuthorizationFilter", "ExceptionTranslationFilter");
        }
        assertThat(facts.get("authenticationManager").get("providers")).extracting(JsonNode::asText)
                .containsExactly("DaoAuthenticationProvider");
        assertThat(facts.get("userDetailsService").asText())
                .isEqualTo(context.getBean(org.springframework.security.core.userdetails.UserDetailsService.class)
                        .getClass().getSimpleName()).isEqualTo("AppUserDetailsService");
        assertThat(facts.get("passwordEncoder").asText()).isEqualTo("DelegatingPasswordEncoder");
        assertThat(facts.get("jwtDecoder").get("type").asText()).isEqualTo(context.getBean(JwtDecoder.class).getClass()
                .getSimpleName()).isEqualTo("NimbusJwtDecoder");
        assertThat(facts.get("jwtDecoder").get("beanCount").asInt()).isEqualTo(1);
        assertThat(facts.get("jwtEncoder").get("beanCount").asInt()).isEqualTo(1);
        assertThat(facts.get("secretKeyBeans").asInt()).isEqualTo(1);
        assertThat(facts.get("jwtAuthenticationConverter").asText()).isEqualTo("DatabaseBackedJwtAuthenticationConverter");
        assertThat(facts.get("lab").get("decoderIsSpringBean").asBoolean()).isFalse();
        assertThat(facts.get("lab").get("usesSeparateKey").asBoolean()).isTrue();
        assertThat(facts.toString()).doesNotContain("secret=").doesNotContain("password=");
    }

    // ------------------------------------------------------------------ stateless authentication

    @Test
    void theLoginAuthenticationManagerIsAnExplicitProviderManagerSoNoReflectionIsNeeded() throws Exception {
        org.springframework.security.authentication.AuthenticationManager manager =
                context.getBean(org.springframework.security.authentication.AuthenticationManager.class);

        // A plain ProviderManager (not Spring's private wrapper), so the providers are public API.
        assertThat(manager).isInstanceOf(org.springframework.security.authentication.ProviderManager.class);
        var providers = ((org.springframework.security.authentication.ProviderManager) manager).getProviders();
        assertThat(providers).hasSize(1);
        assertThat(providers.get(0)).isInstanceOf(org.springframework.security.authentication.dao.DaoAuthenticationProvider.class);

        // ...and the facts endpoint reports exactly that.
        User sam = createUser("sam", Role.STUDENT);
        JsonNode facts = objectMapper.readTree(mockMvc.perform(get("/api/lab/jwt/components")
                .header("Authorization", bearer(sam))).andReturn().getResponse().getContentAsString());
        assertThat(facts.get("authenticationManager").get("type").asText()).isEqualTo("ProviderManager");
        assertThat(facts.get("authenticationManager").get("providers")).extracting(JsonNode::asText)
                .containsExactly("DaoAuthenticationProvider");
    }

    @Test
    void neitherChainCreatesASessionOrCookie() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        JsonNode issued = issue(sam, "VALID", null);

        MockHttpServletResponse lab = mockMvc.perform(get(pathOf("whoami")).header("Authorization",
                "Bearer " + issued.get("token").asText())).andReturn().getResponse();
        MvcResult real = mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(sam))).andReturn();

        assertThat(lab.getStatus()).isEqualTo(200);
        assertThat(lab.getCookies()).isEmpty();
        assertThat(lab.getHeaders("Set-Cookie")).isEmpty();
        assertThat(real.getResponse().getCookies()).isEmpty();
        assertThat(real.getRequest().getSession(false)).isNull();
    }

    @Test
    void theSecurityFilterListInATraceIsTheChainSpringSecurityReportsForTheLabUrl() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        Observed observed = callLabWithToken(sam, pathOf("whoami"), issue(sam, "VALID", null));

        List<String> expected = filterChainProxy.getFilters(pathOf("whoami")).stream()
                .map(f -> f.getClass().getSimpleName()).toList();
        String shown = step(observed.trace(), "security-chain").evidence().get(0).value();

        assertThat(shown).isEqualTo(String.join(" → ", expected));
        assertThat(expected).contains("BearerTokenAuthenticationFilter", "ExceptionTranslationFilter", "AuthorizationFilter");
    }

    @Test
    void tracingStaysLimitedToTheCataloguedLabEndpoints() throws Exception {
        User sam = createUser("sam", Role.STUDENT);
        JsonNode issued = issue(sam, "VALID", null);
        String labAuth = "Bearer " + issued.get("token").asText();
        String ticket = traceTicket(sam);

        // Another lab path that is not catalogued, and a POST to a catalogued path (neither consumes the ticket).
        assertThat(mockMvc.perform(get("/api/lab/secure/unknown").header("Authorization", labAuth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, ticket)).andReturn().getResponse()
                .getHeader(LifecycleTraceFilter.RESPONSE_HEADER)).isNull();
        assertThat(mockMvc.perform(post(pathOf("whoami")).header("Authorization", labAuth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, ticket)).andReturn().getResponse()
                .getHeader(LifecycleTraceFilter.RESPONSE_HEADER)).isNull();
        // No ticket, the old literal "1", or an unknown value.
        for (String bogus : java.util.Arrays.asList(null, "1", "true", java.util.UUID.randomUUID().toString())) {
            MockHttpServletRequestBuilder request = get(pathOf("whoami")).header("Authorization", labAuth);
            if (bogus != null) {
                request.header(LifecycleTraceFilter.REQUEST_HEADER, bogus);
            }
            assertThat(mockMvc.perform(request).andReturn().getResponse().getHeader(LifecycleTraceFilter.RESPONSE_HEADER))
                    .as("header value %s", bogus).isNull();
        }
        // The ticket is still valid for a real, catalogued request.
        assertThat(mockMvc.perform(get(pathOf("whoami")).header("Authorization", labAuth)
                .header(LifecycleTraceFilter.REQUEST_HEADER, ticket)).andReturn().getResponse()
                .getHeader(LifecycleTraceFilter.RESPONSE_HEADER)).isNotNull();
    }
}
