package com.springlab.lab.jwt;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.SignedJWT;
import com.springlab.lab.jwt.TokenInspection.Stage;
import com.springlab.lab.jwt.TokenInspection.Verification;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Looks at a token the way the lab decoder does, but one check at a time so a learner can see which check failed.
 *
 * <p>Honesty about what is real: the DECODING part is plain Base64URL + JSON. The VERIFICATION verdict is the real
 * {@code NimbusJwtDecoder}'s answer. The STAGE list is rebuilt by running the same components separately (Nimbus'
 * {@code MACVerifier}, Spring's {@code JwtTimestampValidator} and {@code JwtIssuerValidator}); it is labelled DERIVED
 * and is cross-checked against the decoder's verdict on every call.
 */
@Component
public class LabJwtInspector {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final String EXPECTED_ALGORITHM = "HS256";

    private final LabJwtService lab;

    public LabJwtInspector(LabJwtService lab) {
        this.lab = lab;
    }

    public TokenInspection inspect(String token) {
        String[] parts = token.split("\\.", -1);
        Map<String, Object> header = null;
        Map<String, Object> claims = null;
        String decodeError = null;
        if (parts.length >= 2) {
            try {
                header = decodeObject(parts[0]);
                claims = decodeObject(parts[1]);
            } catch (Exception e) {
                decodeError = "The header or payload is not Base64URL-encoded JSON";
            }
        } else {
            decodeError = "A JWT in compact form has three dot-separated parts; this has " + parts.length;
        }

        List<Stage> stages = new ArrayList<>();
        boolean failed = false;
        failed = add(stages, failed, "structure", "Three dot-separated parts", parts.length == 3,
                parts.length + " part(s)", "A compact JWT is header.payload.signature");
        failed = add(stages, failed, "decoding", "Header and payload decode to JSON", header != null && claims != null,
                header != null && claims != null ? "decoded without any key" : decodeError, "Decoding needs no key");
        String algorithm = header == null ? null : String.valueOf(header.get("alg"));
        failed = add(stages, failed, "algorithm", "Algorithm is the expected HS256", EXPECTED_ALGORITHM.equals(algorithm),
                "alg = " + algorithm, "The decoder only accepts the algorithm it was configured with");
        failed = verifySignature(stages, failed, token);
        failed = validateClaims(stages, failed, token, header, claims);

        Verification verification = realVerdict(token, stages);
        boolean allPassed = stages.stream().allMatch(s -> s.status().equals("PASSED"));
        return new TokenInspection(token.length(), parts.length, header, claims, signaturePreview(parts), decodeError,
                verification, List.copyOf(stages), allPassed == verification.valid());
    }

    // ------------------------------------------------------------------ stages

    private static boolean add(List<Stage> stages, boolean alreadyFailed, String id, String label, boolean passed,
                               String detail, String hint) {
        String status = alreadyFailed ? "NOT_REACHED" : passed ? "PASSED" : "FAILED";
        stages.add(new Stage(id, label, status, alreadyFailed ? "an earlier check failed" : detail + " - " + hint,
                "DERIVED"));
        return alreadyFailed || !passed;
    }

    private boolean verifySignature(List<Stage> stages, boolean failed, String token) {
        boolean ok = false;
        String detail = "signature checked with the lab key";
        if (!failed) {
            try {
                ok = SignedJWT.parse(token).verify(new MACVerifier(lab.labKeyBytes()));
                detail = ok ? "the signature matches header.payload under the lab key"
                        : "the signature does not match header.payload under the lab key";
            } catch (Exception e) {
                detail = "cannot be parsed as a signed JWT";
            }
        }
        return add(stages, failed, "signature", "Signature verifies with the key", ok, detail,
                "only someone holding the key can produce it");
    }

    private boolean validateClaims(List<Stage> stages, boolean failed, String token, Map<String, Object> header,
                                   Map<String, Object> claims) {
        Jwt jwt = null;
        if (!failed && header != null && claims != null) {
            Map<String, Object> typed = new LinkedHashMap<>(claims);
            for (String time : List.of("exp", "nbf", "iat")) {
                if (typed.get(time) instanceof Number n) {
                    typed.put(time, Instant.ofEpochSecond(n.longValue()));
                }
            }
            jwt = Jwt.withTokenValue(token).headers(h -> h.putAll(header)).claims(c -> c.putAll(typed)).build();
        }
        Map<String, Object> noNbf = withoutKey(jwt, "nbf");
        Map<String, Object> noExp = withoutKey(jwt, "exp");
        failed = claimStage(stages, failed, "expiry", "Not expired (exp)", jwt == null ? null : noNbf,
                new JwtTimestampValidator(), jwt);
        failed = claimStage(stages, failed, "notBefore", "Already valid (nbf)", jwt == null ? null : noExp,
                new JwtTimestampValidator(), jwt);
        JwtIssuerValidator issuer = new JwtIssuerValidator(LabJwtService.LAB_ISSUER);
        return claimStage(stages, failed, "issuer", "Issuer is the expected one (iss)", jwt == null ? null : Map.of(),
                issuer, jwt);
    }

    private static Map<String, Object> withoutKey(Jwt jwt, String key) {
        if (jwt == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(jwt.getClaims());
        copy.remove(key);
        return copy;
    }

    /** Runs one real validator; {@code claimView} (when non-empty) selects which time claim the validator may see. */
    private static boolean claimStage(List<Stage> stages, boolean failed, String id, String label,
                                      Map<String, Object> claimView, org.springframework.security.oauth2.core.OAuth2TokenValidator<Jwt> validator,
                                      Jwt jwt) {
        boolean passed = false;
        String detail = "not evaluated";
        if (!failed && jwt != null) {
            Jwt view = claimView == null || claimView.isEmpty() ? jwt
                    : Jwt.withTokenValue(jwt.getTokenValue()).headers(h -> h.putAll(jwt.getHeaders()))
                    .claims(c -> c.putAll(claimView)).build();
            OAuth2TokenValidatorResult result = validator.validate(view);
            passed = !result.hasErrors();
            detail = passed ? "accepted" : result.getErrors().stream().map(OAuth2Error::getDescription).findFirst()
                    .orElse("rejected");
        }
        return add(stages, failed, id, label, passed, detail, "checked after the signature");
    }

    // ------------------------------------------------------------------ the real decoder's verdict

    private Verification realVerdict(String token, List<Stage> stages) {
        try {
            lab.decoder().decode(token);
            return new Verification(true, "VALID", "accepted by the lab JwtDecoder", "CAPTURED");
        } catch (JwtException e) {
            return new Verification(false, categoryOf(stages), e.getMessage(), "CAPTURED");
        }
    }

    /** The category is the first failed stage of the reconstruction (the decoder only gives free text). */
    private static String categoryOf(List<Stage> stages) {
        for (Stage stage : stages) {
            if (stage.status().equals("FAILED")) {
                return switch (stage.id()) {
                    case "structure", "decoding" -> "MALFORMED";
                    case "algorithm" -> "UNSUPPORTED_ALGORITHM";
                    case "signature" -> "BAD_SIGNATURE";
                    case "expiry" -> "EXPIRED";
                    case "notBefore" -> "NOT_YET_VALID";
                    case "issuer" -> "WRONG_ISSUER";
                    default -> "INVALID";
                };
            }
        }
        return "INVALID";
    }

    // ------------------------------------------------------------------ helpers

    private static Map<String, Object> decodeObject(String segment) throws Exception {
        return JSON.readValue(new String(B64D.decode(segment), StandardCharsets.UTF_8), new TypeReference<>() { });
    }

    /** Only a short prefix of the signature is shown; the full value adds nothing for learning. */
    private static String signaturePreview(String[] parts) {
        if (parts.length < 3 || parts[2].isEmpty()) {
            return "(empty)";
        }
        String s = parts[2];
        return (s.length() > 12 ? s.substring(0, 12) + "…" : s) + " (" + s.length() + " characters)";
    }
}
