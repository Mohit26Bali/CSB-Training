package com.csb.banking.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import io.jsonwebtoken.JwtException;

/** Unit tests of {@link JwtService}: round trip, tampering, a different signing key, expiry, and the secret check. */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-1234";

    JwtService service = new JwtService(SECRET, 30);

    @Test
    void aTokenRoundTripsTheUsername() {
        String token = service.generateToken("ravi", "CUSTOMER");
        assertEquals("ravi", service.extractUsername(token));
    }

    @Test
    void theLifetimeIsReportedInSeconds() {
        assertEquals(1800, service.getExpiresInSeconds());
    }

    @Test
    void aTamperedTokenIsRejected() {
        String token = service.generateToken("ravi", "CUSTOMER");
        // change the payload part: the signature no longer matches
        String[] parts = token.split("\\.");
        String forged = parts[0] + "." + parts[1].substring(0, parts[1].length() - 2) + "AA." + parts[2];
        assertThrows(JwtException.class, () -> service.extractUsername(forged));
    }

    @Test
    void aTokenSignedWithAnotherSecretIsRejected() {
        String foreign = new JwtService("a-completely-different-secret-1234567890", 30).generateToken("ravi", "ADMIN");
        assertThrows(JwtException.class, () -> service.extractUsername(foreign));
    }

    @Test
    void anExpiredTokenIsRejected() {
        String expired = new JwtService(SECRET, -1).generateToken("ravi", "CUSTOMER");
        assertThrows(JwtException.class, () -> service.extractUsername(expired));
    }

    @Test
    void garbageIsRejected() {
        assertThrows(JwtException.class, () -> service.extractUsername("not-a-jwt"));
    }

    @Test
    void aShortSecretIsRefusedAtStartup() {
        assertThrows(IllegalStateException.class, () -> new JwtService("too-short", 30));
    }
}
