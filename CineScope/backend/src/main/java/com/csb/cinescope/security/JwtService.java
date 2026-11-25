package com.csb.cinescope.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Creates and verifies the signed login tokens (JWT, HS256).
 *
 * <p>{@code service/AuthService} calls {@link #generateToken} after a successful login or registration;
 * {@link JwtAuthenticationFilter} calls {@link #extractUsername} on every request that carries a token. Secret and lifetime come
 * from {@code app.jwt.*} in application.properties (override {@code JWT_SECRET} outside local development). The token holds
 * the username and a token version; the user is re-read from the database on each request, so a disabled account is locked
 * out at once, and a token issued before a password or role change (older version) is refused.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.validity = Duration.ofMinutes(expirationMinutes);
    }

    /** What a valid token says: whose it is and which token version of the account it was issued under. */
    public record TokenClaims(String username, int tokenVersion) {
    }

    public String generateToken(String username) {
        return generateToken(username, 0);
    }

    /** A token for the account at this token version; the filter refuses it once the account's version has moved on. */
    public String generateToken(String username, int tokenVersion) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("tv", tokenVersion)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity.toMillis()))
                .signWith(key)
                .compact();
    }

    /** Returns the claims if the token is valid and unexpired; throws a JwtException otherwise. A token without a version counts as 0. */
    public TokenClaims verify(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        Integer version = claims.get("tv", Integer.class);
        return new TokenClaims(claims.getSubject(), version == null ? 0 : version);
    }

    /** Returns the username if the token is valid and unexpired; throws a JwtException otherwise. */
    public String extractUsername(String token) {
        return verify(token).username();
    }

    public long getExpiresInSeconds() {
        return validity.toSeconds();
    }
}
