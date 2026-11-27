package com.springlab.lab.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.lab.code.CodeReference;
import com.springlab.lab.jwt.JwtSimulatorCatalog.Experiment;
import com.springlab.lab.trace.CodeRefIds;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/** Structural checks of the simulator's catalog (the behavioural checks run against the real application elsewhere). */
class JwtCatalogConsistencyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static JwtSimulatorCatalog.Catalog catalog;
    private static Set<String> codeRefIds;

    @BeforeAll
    static void load() throws Exception {
        try (InputStream in = new ClassPathResource("lab/jwt-simulator-catalog.json").getInputStream()) {
            catalog = MAPPER.readValue(in, JwtSimulatorCatalog.Catalog.class);
        }
        try (InputStream in = new ClassPathResource("lab/code-references.json").getInputStream()) {
            codeRefIds = MAPPER.readValue(in, new TypeReference<List<CodeReference>>() { }).stream()
                    .map(CodeReference::id).collect(Collectors.toSet());
        }
    }

    @Test
    void everyTokenFaultTheServerCanIssueIsCataloguedAndNothingElseIs() {
        Set<String> catalogued = catalog.variants().stream().map(JwtSimulatorCatalog.Variant::id).collect(Collectors.toSet());
        Set<String> issuable = Arrays.stream(LabTokenVariant.values()).map(Enum::name).collect(Collectors.toSet());

        assertThat(catalogued).containsAll(issuable);
        assertThat(catalogued).contains("NO_TOKEN");
        Set<String> extra = new HashSet<>(catalogued);
        extra.removeAll(issuable);
        assertThat(extra).containsExactly("NO_TOKEN");
        catalog.variants().forEach(v -> assertThat(v.issued()).as(v.id()).isEqualTo(!v.id().equals("NO_TOKEN")));
    }

    @Test
    void failingStagesNameRealChecks() {
        Set<String> stages = Set.of("structure", "decoding", "algorithm", "signature", "expiry", "notBefore", "issuer");
        catalog.variants().forEach(v -> {
            if (v.failingStage() != null) {
                assertThat(stages).as(v.id()).contains(v.failingStage());
            }
        });
        assertThat(catalog.variants().stream().filter(v -> v.failingStage() == null).map(JwtSimulatorCatalog.Variant::id))
                .containsExactlyInAnyOrder("VALID", "NO_TOKEN");
    }

    @Test
    void targetsAreTheLabSecureEndpointsAndOnlyGetRequests() {
        assertThat(catalog.targets()).extracting(JwtSimulatorCatalog.Target::path).containsExactlyInAnyOrder(
                "/api/lab/secure/whoami", "/api/lab/secure/teacher-area", "/api/lab/secure/admin-area");
        assertThat(catalog.targets()).allSatisfy(t -> assertThat(t.method()).isEqualTo("GET"));
    }

    @Test
    void experimentsAreWellFormed() {
        Set<String> targets = catalog.targets().stream().map(JwtSimulatorCatalog.Target::id).collect(Collectors.toSet());
        Set<String> variants = catalog.variants().stream().map(JwtSimulatorCatalog.Variant::id).collect(Collectors.toSet());
        Set<String> roleKeys = Set.of("*", "STUDENT", "TEACHER", "ADMIN");
        Set<String> ids = new HashSet<>();

        for (Experiment e : catalog.experiments()) {
            assertThat(ids.add(e.id())).as("duplicate %s", e.id()).isTrue();
            assertThat(e.kind()).isIn("LIVE", "SIMULATED");
            assertThat(e.group()).isIn("authentication", "authorization");
            assertThat(e.changes()).isNotBlank();
            assertThat(e.consequence()).isNotBlank();
            if (e.codeRefId() != null) {
                assertThat(codeRefIds).as("%s code ref", e.id()).contains(e.codeRefId());
            }
            if (e.kind().equals("LIVE")) {
                assertThat(targets).contains(e.target());
                assertThat(variants).contains(e.variant());
                if (e.claimedRole() != null) {
                    assertThat(Arrays.stream(Role.values()).map(Enum::name)).contains(e.claimedRole());
                }
                assertThat(roleKeys).containsAll(e.expectation().keySet());
                e.expectation().values().forEach(x -> {
                    assertThat(x.status()).isNotNull();
                    assertThat(x.handlerInvoked()).isNotNull();
                    assertThat(x.sqlCount()).isNotNull();
                    if (x.challengeContains() != null) {
                        assertThat(x.status()).as("%s: only 401 carries a token error", e.id()).isEqualTo(401);
                    }
                });
            } else {
                assertThat(e.target()).isNull();
                assertThat(e.variant()).isNull();
                assertThat(e.expectation()).isNull();
                assertThat(e.realBehaviourReference()).isNotBlank();
            }
        }
    }

    @Test
    void theLessonCoversEveryRequiredFailureAndTheAuthorizationExperiments() {
        Set<String> live = catalog.experiments().stream().filter(e -> e.kind().equals("LIVE")).map(Experiment::id)
                .collect(Collectors.toSet());
        assertThat(live).contains("valid-token", "expired-token", "not-yet-valid", "wrong-signing-key", "wrong-issuer",
                "tampered-signature", "tampered-payload", "malformed-token", "algorithm-none", "no-token",
                "student-calls-admin-area", "claim-not-authoritative", "claim-understated", "teacher-area");
        assertThat(catalog.experiments().stream().filter(e -> e.kind().equals("SIMULATED")).count())
                .isGreaterThanOrEqualTo(2);
    }

    @Test
    void authenticationFailuresAre401WithoutTouchingTheDatabaseAndAuthorizationFailuresAre403AfterOneLookup() {
        for (Experiment e : catalog.experiments()) {
            if (!e.kind().equals("LIVE")) {
                continue;
            }
            e.expectation().forEach((role, x) -> {
                if (x.status() == 401) {
                    assertThat(x.sqlCount()).as("%s/%s: a rejected token costs no SQL", e.id(), role).isZero();
                    assertThat(x.handlerInvoked()).isFalse();
                }
                if (x.status() == 403) {
                    assertThat(x.sqlCount()).as("%s/%s: 403 follows exactly one role lookup", e.id(), role).isEqualTo(1);
                    assertThat(x.handlerInvoked()).isFalse();
                }
            });
        }
    }

    @Test
    void jwtCodeReferencesAreRegistered() {
        assertThat(codeRefIds).containsAll(Set.of(CodeRefIds.JWT_DECODER_CONFIG, CodeRefIds.JWT_ISSUING,
                CodeRefIds.LAB_SECURE_CHAIN, CodeRefIds.LAB_JWT_SERVICE, CodeRefIds.LAB_SECURE_CONTROLLER,
                CodeRefIds.LOGIN_SERVICE, CodeRefIds.USER_DETAILS_SERVICE, CodeRefIds.SECURITY_BEANS));
    }
}
