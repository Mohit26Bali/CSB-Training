package com.springlab.lab.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.springlab.entity.Role;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;

/**
 * The simulator's own token machinery. It is intentionally isolated from the application's real JWT configuration:
 *
 * <ul>
 *   <li>The lab signing key is random, generated in memory when the application starts, never configured, logged or
 *       returned, and it changes on every restart (lab tokens do not survive a restart).</li>
 *   <li>A second, unrelated key is used for the WRONG_SIGNING_KEY experiment.</li>
 *   <li>The key, encoder and decoder are plain fields of this class. They are NOT Spring beans, so the application
 *       still has exactly one {@code SecretKey}, one {@code JwtEncoder} and one {@code JwtDecoder} bean, and every
 *       injection point in the real configuration is unchanged. The decoder is handed to the lab filter chain as an
 *       instance ({@code jwt.decoder(...)}), which needs no bean.</li>
 * </ul>
 */
@Component
public class LabJwtService {

    public static final String LAB_ISSUER = "springlab-lab";
    public static final String OTHER_ISSUER = "https://not-springlab.example";
    public static final Duration VALID_LIFETIME = Duration.ofMinutes(5);
    public static final String ROLE_CLAIM = "role";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();

    private final SecretKey labKey;
    private final JwtEncoder labEncoder;
    private final JwtEncoder foreignEncoder;
    private final NimbusJwtDecoder labDecoder;

    public LabJwtService() {
        SecureRandom random = new SecureRandom();
        this.labKey = randomKey(random);
        this.labEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(labKey));
        this.foreignEncoder = new NimbusJwtEncoder(new ImmutableSecret<>(randomKey(random)));
        // Same decoder class and the same default validators (exp / nbf with 60 s skew) plus an issuer check as the
        // application's real decoder; only the key and the expected issuer differ.
        this.labDecoder = NimbusJwtDecoder.withSecretKey(labKey).macAlgorithm(MacAlgorithm.HS256).build();
        this.labDecoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(LAB_ISSUER));
    }

    private static SecretKey randomKey(SecureRandom random) {
        byte[] bytes = new byte[32]; // 256 bits, the minimum for HS256
        random.nextBytes(bytes);
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** The decoder used by the lab filter chain. It is returned as an instance and never registered as a bean. */
    public JwtDecoder decoder() {
        return labDecoder;
    }

    /** Package-private: only the inspector in this package may verify with the lab key. */
    byte[] labKeyBytes() {
        return labKey.getEncoded();
    }

    /**
     * Builds a token of the requested kind. The subject is always the caller's own username, so a lab token can only
     * ever describe the caller. {@code actualRole} is the role stored in the database; {@code claimedRole} is what the
     * token's role claim says (it may deliberately differ: the claim is not authoritative).
     */
    public String issue(LabTokenVariant variant, String username, Role actualRole, Role claimedRole) {
        Instant now = Instant.now();
        Role claim = claimedRole == null ? actualRole : claimedRole;
        Instant valid = now.plus(VALID_LIFETIME);
        return switch (variant) {
            case VALID -> sign(labEncoder, LAB_ISSUER, username, now, valid, null, claim);
            case EXPIRED -> sign(labEncoder, LAB_ISSUER, username, now.minus(Duration.ofMinutes(20)),
                    now.minus(Duration.ofMinutes(10)), null, claim);
            case NOT_YET_VALID -> sign(labEncoder, LAB_ISSUER, username, now, now.plus(Duration.ofMinutes(20)),
                    now.plus(Duration.ofMinutes(10)), claim);
            case WRONG_SIGNING_KEY -> sign(foreignEncoder, LAB_ISSUER, username, now, valid, null, claim);
            case WRONG_ISSUER -> sign(labEncoder, OTHER_ISSUER, username, now, valid, null, claim);
            case TAMPERED_SIGNATURE -> tamperSignature(sign(labEncoder, LAB_ISSUER, username, now, valid, null, claim));
            case TAMPERED_PAYLOAD -> tamperPayload(sign(labEncoder, LAB_ISSUER, username, now, valid, null, actualRole),
                    forgedRole(actualRole, claimedRole));
            case MALFORMED -> dropSignature(sign(labEncoder, LAB_ISSUER, username, now, valid, null, claim));
            case ALGORITHM_NONE -> unsigned(sign(labEncoder, LAB_ISSUER, username, now, valid, null, claim));
        };
    }

    private static String sign(JwtEncoder encoder, String issuer, String subject, Instant issuedAt, Instant expiresAt,
                               Instant notBefore, Role role) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(issuer).subject(subject).issuedAt(issuedAt).expiresAt(expiresAt)
                .id(UUID.randomUUID().toString()).claim(ROLE_CLAIM, role.name());
        if (notBefore != null) {
            claims.notBefore(notBefore);
        }
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();
    }

    /** Changes the first character of the signature: this always changes the decoded signature bytes. */
    static String tamperSignature(String token) {
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        return parts[0] + "." + parts[1] + "." + first + parts[2].substring(1);
    }

    /** Replaces the payload's role claim but keeps the header and the ORIGINAL signature. */
    static String tamperPayload(String token, Role forged) {
        String[] parts = token.split("\\.");
        try {
            Map<String, Object> claims = JSON.readValue(B64D.decode(parts[1]), new TypeReference<>() { });
            claims.put(ROLE_CLAIM, forged.name());
            String payload = B64.encodeToString(JSON.writeValueAsBytes(claims));
            return parts[0] + "." + payload + "." + parts[2];
        } catch (IOException e) {
            throw new IllegalStateException("Cannot build the tampered lab token", e);
        }
    }

    static String dropSignature(String token) {
        String[] parts = token.split("\\.");
        return parts[0] + "." + parts[1];
    }

    /** An unsecured JWT ("alg": "none"): the same payload, no signature. */
    static String unsigned(String token) {
        String[] parts = token.split("\\.");
        String header = B64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
        return header + "." + parts[1] + ".";
    }

    /** The forged role always differs from the real one, otherwise the "tampering" would not change anything. */
    static Role forgedRole(Role actual, Role requested) {
        if (requested != null && requested != actual) {
            return requested;
        }
        return actual == Role.ADMIN ? Role.TEACHER : Role.ADMIN;
    }
}
