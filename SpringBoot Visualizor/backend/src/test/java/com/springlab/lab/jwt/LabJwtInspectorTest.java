package com.springlab.lab.jwt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.lab.jwt.TokenInspection.Stage;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.oauth2.jwt.JwtException;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure unit tests (no Spring context): every token fault is issued, then inspected, and the reconstruction must agree
 * with the real lab JwtDecoder's own verdict.
 */
class LabJwtInspectorTest {

    private final LabJwtService lab = new LabJwtService();
    private final LabJwtInspector inspector = new LabJwtInspector(lab);

    private static Map<String, JwtSimulatorCatalog.Variant> variants() throws Exception {
        try (InputStream in = new ClassPathResource("lab/jwt-simulator-catalog.json").getInputStream()) {
            return new ObjectMapper().readValue(in, JwtSimulatorCatalog.Catalog.class).variants().stream()
                    .collect(Collectors.toMap(JwtSimulatorCatalog.Variant::id, v -> v));
        }
    }

    private TokenInspection inspect(LabTokenVariant variant) {
        return inspector.inspect(lab.issue(variant, "sam", Role.STUDENT, null));
    }

    private static String firstFailed(TokenInspection inspection) {
        return inspection.stages().stream().filter(s -> s.status().equals("FAILED")).map(Stage::id).findFirst().orElse(null);
    }

    // ------------------------------------------------------------------ verification vs the real decoder

    @Test
    void onlyTheValidTokenIsAcceptedAndTheStageReconstructionAlwaysAgreesWithTheDecoder() {
        for (LabTokenVariant variant : LabTokenVariant.values()) {
            TokenInspection inspection = inspect(variant);

            assertThat(inspection.verification().valid()).as("%s accepted by the decoder", variant)
                    .isEqualTo(variant == LabTokenVariant.VALID);
            assertThat(inspection.stagesAgreeWithDecoder()).as("%s: reconstruction vs decoder", variant).isTrue();
            assertThat(inspection.verification().provenance()).isEqualTo("CAPTURED");
            assertThat(inspection.stages()).allSatisfy(s -> assertThat(s.provenance()).isEqualTo("DERIVED"));
        }
    }

    @Test
    void theFailingCheckOfEveryVariantMatchesWhatTheCatalogTeaches() throws Exception {
        Map<String, JwtSimulatorCatalog.Variant> catalog = variants();
        for (LabTokenVariant variant : LabTokenVariant.values()) {
            TokenInspection inspection = inspect(variant);

            assertThat(firstFailed(inspection)).as("first failing check of %s", variant)
                    .isEqualTo(catalog.get(variant.name()).failingStage());
        }
    }

    @Test
    void checksAfterTheFirstFailureAreMarkedNotReached() {
        for (LabTokenVariant variant : LabTokenVariant.values()) {
            List<Stage> stages = inspect(variant).stages();
            boolean failedSeen = false;
            for (Stage stage : stages) {
                if (failedSeen) {
                    assertThat(stage.status()).as("%s after a failure: %s", variant, stage.id()).isEqualTo("NOT_REACHED");
                }
                failedSeen |= stage.status().equals("FAILED");
            }
        }
    }

    @Test
    void categoriesNameTheKindOfFailure() {
        assertThat(inspect(LabTokenVariant.VALID).verification().category()).isEqualTo("VALID");
        assertThat(inspect(LabTokenVariant.EXPIRED).verification().category()).isEqualTo("EXPIRED");
        assertThat(inspect(LabTokenVariant.NOT_YET_VALID).verification().category()).isEqualTo("NOT_YET_VALID");
        assertThat(inspect(LabTokenVariant.WRONG_ISSUER).verification().category()).isEqualTo("WRONG_ISSUER");
        assertThat(inspect(LabTokenVariant.WRONG_SIGNING_KEY).verification().category()).isEqualTo("BAD_SIGNATURE");
        assertThat(inspect(LabTokenVariant.TAMPERED_SIGNATURE).verification().category()).isEqualTo("BAD_SIGNATURE");
        assertThat(inspect(LabTokenVariant.TAMPERED_PAYLOAD).verification().category()).isEqualTo("BAD_SIGNATURE");
        assertThat(inspect(LabTokenVariant.MALFORMED).verification().category()).isEqualTo("MALFORMED");
        assertThat(inspect(LabTokenVariant.ALGORITHM_NONE).verification().category()).isEqualTo("UNSUPPORTED_ALGORITHM");
    }

    @Test
    void theFrameworkMessageIsTheRealDecodersAndTheSignatureCheckPrecedesTheClaimChecks() {
        assertThat(inspect(LabTokenVariant.EXPIRED).verification().frameworkMessage()).containsIgnoringCase("expired");
        assertThat(inspect(LabTokenVariant.NOT_YET_VALID).verification().frameworkMessage()).contains("before");
        assertThat(inspect(LabTokenVariant.WRONG_ISSUER).verification().frameworkMessage()).contains("iss");
        assertThat(inspect(LabTokenVariant.TAMPERED_SIGNATURE).verification().frameworkMessage())
                .containsIgnoringCase("signature");
        // An expired AND wrongly signed token fails on the signature: claims are only checked after it verifies.
        TokenInspection both = inspector.inspect(forgeExpiredWithWrongKey());
        assertThat(both.verification().category()).isEqualTo("BAD_SIGNATURE");
    }

    private String forgeExpiredWithWrongKey() {
        String expired = lab.issue(LabTokenVariant.EXPIRED, "sam", Role.STUDENT, null);
        return LabJwtService.tamperSignature(expired);
    }

    // ------------------------------------------------------------------ decoding is not verification

    @Test
    void anyTokenCanBeDecodedWithoutAKeyEvenOnesThatFailVerification() {
        for (LabTokenVariant variant : LabTokenVariant.values()) {
            TokenInspection inspection = inspect(variant);

            assertThat(inspection.header()).as("%s header", variant).isNotNull();
            assertThat(inspection.claims()).as("%s claims", variant).isNotNull();
            assertThat(inspection.claims().get("sub")).isEqualTo("sam");
            assertThat(inspection.decodeError()).isNull();
        }
    }

    @Test
    void aTamperedPayloadShowsTheForgedRoleEvenThoughVerificationRejectsIt() {
        TokenInspection inspection = inspect(LabTokenVariant.TAMPERED_PAYLOAD);

        assertThat(inspection.claims().get("role")).isEqualTo("ADMIN"); // readable and forged...
        assertThat(inspection.verification().valid()).isFalse();        // ...but the signature no longer matches
        assertThat(firstFailed(inspection)).isEqualTo("signature");
    }

    @Test
    void theAlgorithmNoneTokenIsRejectedBecauseTheServerDecidesTheAlgorithm() {
        TokenInspection inspection = inspect(LabTokenVariant.ALGORITHM_NONE);

        assertThat(inspection.header().get("alg")).isEqualTo("none");
        assertThat(inspection.signaturePreview()).isEqualTo("(empty)");
        assertThat(firstFailed(inspection)).isEqualTo("algorithm");
        assertThat(inspection.stages()).filteredOn(s -> s.id().equals("signature")).first()
                .satisfies(s -> assertThat(s.status()).isEqualTo("NOT_REACHED"));
    }

    // ------------------------------------------------------------------ what a valid token contains

    @Test
    void aValidTokenCarriesTheDocumentedHeaderAndClaims() {
        TokenInspection inspection = inspect(LabTokenVariant.VALID);

        assertThat(inspection.parts()).isEqualTo(3);
        assertThat(inspection.header()).containsEntry("alg", "HS256");
        assertThat(inspection.claims()).containsEntry("iss", LabJwtService.LAB_ISSUER).containsEntry("sub", "sam")
                .containsEntry("role", "STUDENT").containsKeys("iat", "exp", "jti");
        long lifetime = ((Number) inspection.claims().get("exp")).longValue() - ((Number) inspection.claims().get("iat")).longValue();
        assertThat(lifetime).isEqualTo(LabJwtService.VALID_LIFETIME.toSeconds());
        assertThat(inspection.stages()).allSatisfy(s -> assertThat(s.status()).isEqualTo("PASSED"));
    }

    @Test
    void theClaimedRoleIsWhateverWasRequestedAndNeverChangesTheDatabaseRole() {
        String token = lab.issue(LabTokenVariant.VALID, "sam", Role.STUDENT, Role.ADMIN);
        TokenInspection inspection = inspector.inspect(token);

        assertThat(inspection.claims().get("role")).isEqualTo("ADMIN"); // the claim is whatever the learner asked for
        assertThat(inspection.verification().valid()).isTrue();         // and the token is still genuinely valid
    }

    @Test
    void theForgedRoleAlwaysDiffersFromTheRealRole() {
        assertThat(LabJwtService.forgedRole(Role.STUDENT, null)).isEqualTo(Role.ADMIN);
        assertThat(LabJwtService.forgedRole(Role.STUDENT, Role.TEACHER)).isEqualTo(Role.TEACHER);
        assertThat(LabJwtService.forgedRole(Role.ADMIN, Role.ADMIN)).isEqualTo(Role.TEACHER);
        assertThat(LabJwtService.forgedRole(Role.TEACHER, Role.TEACHER)).isEqualTo(Role.ADMIN);
    }

    // ------------------------------------------------------------------ keys and arbitrary input

    @Test
    void aTokenFromAnotherLabInstanceIsRejectedBecauseEachInstanceHasItsOwnRandomKey() {
        LabJwtService other = new LabJwtService();
        String foreign = other.issue(LabTokenVariant.VALID, "sam", Role.STUDENT, null);

        assertThatThrownBy(() -> lab.decoder().decode(foreign)).isInstanceOf(JwtException.class);
        assertThat(inspector.inspect(foreign).verification().category()).isEqualTo("BAD_SIGNATURE");
    }

    @Test
    void garbageInputIsReportedAsMalformedNotAsAnError() {
        for (String garbage : new String[]{"abc", "a.b", "..", "a.b.c", "eyJhbGciOiJIUzI1NiJ9.e30.", "not a token at all"}) {
            TokenInspection inspection = inspector.inspect(garbage);

            assertThat(inspection.verification().valid()).as(garbage).isFalse();
            assertThat(inspection.stagesAgreeWithDecoder()).as(garbage).isTrue();
        }
        assertThat(inspector.inspect("abc").decodeError()).contains("three");
    }

    @Test
    void onlyAShortSignaturePrefixIsEverShown() {
        String token = lab.issue(LabTokenVariant.VALID, "sam", Role.STUDENT, null);
        String signature = token.split("\\.")[2];

        TokenInspection inspection = inspector.inspect(token);

        assertThat(inspection.signaturePreview()).doesNotContain(signature).contains("…")
                .contains(String.valueOf(signature.length()));
    }

    @Test
    void theInspectionNeverContainsTheLabKey() throws Exception {
        java.lang.reflect.Field field = LabJwtService.class.getDeclaredField("labKey");
        field.setAccessible(true);
        byte[] key = ((javax.crypto.SecretKey) field.get(lab)).getEncoded();
        String json = new ObjectMapper().writeValueAsString(inspect(LabTokenVariant.VALID));

        assertThat(json).doesNotContain(java.util.Base64.getEncoder().encodeToString(key))
                .doesNotContain(java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key))
                .doesNotContain(java.util.HexFormat.of().formatHex(key));
    }
}
