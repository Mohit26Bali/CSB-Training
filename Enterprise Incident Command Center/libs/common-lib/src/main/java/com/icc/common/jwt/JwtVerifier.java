package com.icc.common.jwt;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

public class JwtVerifier {

    private final SecretKey key;

    public JwtVerifier(String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public JwtClaims verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String roleText = claims.get("role", String.class);
            if (roleText == null) {
                throw new InvalidTokenException("Token has no role");
            }
            Object teamId = claims.get("teamId");

            return new JwtClaims(
                    Long.valueOf(claims.getSubject()),
                    claims.get("username", String.class),
                    Role.valueOf(roleText),
                    teamId == null ? null : ((Number) teamId).longValue());
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidTokenException("Invalid or expired token", e);
        }
    }
}
