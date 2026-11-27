package com.icc.common.jwt;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.WeakKeyException;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtVerifierTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";
    private static final String OTHER_SECRET = "other-secret-other-secret-other-secret-99";

    private final JwtVerifier verifier = new JwtVerifier(SECRET);

    private String token(String secret, String role, Long teamId, Date expiry) {
        JwtBuilder builder = Jwts.builder()
                .subject("7")
                .claim("username", "asha")
                .claim("role", role)
                .expiration(expiry)
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)));
        if (teamId != null) {
            builder.claim("teamId", teamId);
        }
        return builder.compact();
    }

    private Date inOneHour() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    @Test
    void validToken_returnsClaims() {
        String token = token(SECRET, "SUPPORT_ENGINEER", 3L, inOneHour());

        JwtClaims claims = verifier.verify(token);

        assertEquals(new JwtClaims(7L, "asha", Role.SUPPORT_ENGINEER, 3L), claims);
    }

    @Test
    void employeeToken_hasNoTeam() {
        String token = token(SECRET, "EMPLOYEE", null, inOneHour());

        JwtClaims claims = verifier.verify(token);

        assertEquals(Role.EMPLOYEE, claims.role());
        assertNull(claims.teamId());
    }

    @Test
    void expiredToken_isRejected() {
        String token = token(SECRET, "ADMIN", null, new Date(System.currentTimeMillis() - 60_000));

        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void forgedToken_isRejected() {
        String employee = token(SECRET, "EMPLOYEE", null, inOneHour());
        String admin = token(SECRET, "ADMIN", null, inOneHour());
        String[] e = employee.split("\\.");
        String[] a = admin.split("\\.");
        // employee header and signature, but the payload of an admin token
        String forged = e[0] + "." + a[1] + "." + e[2];

        assertThrows(InvalidTokenException.class, () -> verifier.verify(forged));
    }

    @Test
    void tokenSignedWithOtherSecret_isRejected() {
        String token = token(OTHER_SECRET, "ADMIN", null, inOneHour());

        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void garbageText_isRejected() {
        assertThrows(InvalidTokenException.class, () -> verifier.verify("not-a-token"));
    }

    @Test
    void emptyString_isRejected() {
        assertThrows(InvalidTokenException.class, () -> verifier.verify(""));
    }

    @Test
    void unknownRole_isRejected() {
        String token = token(SECRET, "SUPERUSER", null, inOneHour());

        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void missingRole_isRejected() {
        String token = token(SECRET, null, null, inOneHour());

        assertThrows(InvalidTokenException.class, () -> verifier.verify(token));
    }

    @Test
    void shortSecret_failsAtStartup() {
        assertThrows(WeakKeyException.class, () -> new JwtVerifier("short"));
    }
}
