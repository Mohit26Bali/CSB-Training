package com.csb.cinescope.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

/** Unit tests of {@link JwtService}: round trip, tampering, a different signing key, expiry, garbage and the secret check. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    JwtService service = new JwtService(SECRET, 720);

    @Test
    void aTokenRoundTripsTheUsername() {
        assertEquals("asha", service.extractUsername(service.generateToken("asha")));
    }

    @Test
    void theLifetimeIsReportedInSeconds() {
        assertEquals(720 * 60, service.getExpiresInSeconds());
    }

    @Test
    void aTamperedTokenIsRejected() {
        String[] parts = service.generateToken("asha").split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        assertThrows(JwtException.class, () -> service.extractUsername(forged));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtService("a-completely-different-secret-1234567890", 720).generateToken("asha");
        assertThrows(JwtException.class, () -> service.extractUsername(foreign));
    }

    @Test
    void anExpiredTokenIsRejected() {
        String expired = new JwtService(SECRET, -1).generateToken("asha");
        assertThrows(JwtException.class, () -> service.extractUsername(expired));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(JwtException.class, () -> service.extractUsername("not-a-jwt"));
    }

    @Test
    void theTokenVersionTravelsInsideTheToken() {
        JwtService.TokenClaims claims = service.verify(service.generateToken("asha", 7));

        assertEquals("asha", claims.username());
        assertEquals(7, claims.tokenVersion());
        assertEquals(0, service.verify(service.generateToken("asha")).tokenVersion());
    }

    @Test
    void aTokenWithoutAVersionCountsAsVersionZero() {
        // an old token, issued before token versions existed
        String old = io.jsonwebtoken.Jwts.builder().subject("asha").expiration(new java.util.Date(System.currentTimeMillis() + 60_000))
                .signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(SECRET.getBytes(java.nio.charset.StandardCharsets.UTF_8))).compact();

        assertEquals(0, service.verify(old).tokenVersion());
    }

    @Test
    void aShortSecretIsRefusedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 720));
    }
}
