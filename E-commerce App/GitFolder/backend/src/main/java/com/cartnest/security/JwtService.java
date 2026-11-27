package com.cartnest.security;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {
    private static final String ISSUER = "cartnest";

    private final SecretKey key;
    private final Duration validity;

    public JwtService(@Value("${app.jwt.secret}") String secret, @Value("${app.jwt.validity}") Duration validity) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.validity = validity;
    }

    public IssuedToken issue(SecurityUser user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(validity);
        String token = Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(key)
                .compact();
        return new IssuedToken(token, expiresAt);
    }

    public Long userIdFrom(String token) {
        String subject = Jwts.parser().verifyWith(key).requireIssuer(ISSUER).build()
                .parseSignedClaims(token).getPayload().getSubject();
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException ex) {
            throw new JwtException("Token subject is not a user id");
        }
    }

    public record IssuedToken(String token, Instant expiresAt) {
    }
}
