package com.csb.banking.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Creates and verifies the signed JWTs (HS256).
 *
 * <p>Connections:
 * <ul>
 *   <li>{@code service/AuthService} calls {@link #generateToken} after a successful login or registration.</li>
 *   <li>{@link JwtAuthenticationFilter} calls {@link #extractUsername} on every request that carries a token.</li>
 *   <li>The secret and lifetime come from {@code app.jwt.*} in application.properties; the secret must be
 *       provided through the {@code JWT_SECRET} environment variable outside local development.</li>
 * </ul>
 * The token holds only the username (subject) and the role; authorities are re-read from the database on each
 * request by the filter, so a disabled user is locked out immediately.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 characters");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.validity = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(String username, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + validity.toMillis()))
                .signWith(key)
                .compact();
    }

    /** Returns the username if the token is valid and unexpired; throws a JwtException otherwise. */
    public String extractUsername(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload().getSubject();
    }

    public long getExpiresInSeconds() {
        return validity.toSeconds();
    }
}
