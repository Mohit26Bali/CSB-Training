package com.icc.assignment.service;

import com.icc.assignment.entity.AppUser;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Creates the JWT at login. Only this service creates tokens (D7); every other module
 * only verifies them with JwtVerifier from common-lib, using the same JWT_SECRET.
 */
@Service
public class TokenService {

    private final SecretKey key;
    private final long expiryMinutes;

    public TokenService(@Value("${JWT_SECRET}") String secret,
                        @Value("${icc.jwt.expiry-minutes:60}") long expiryMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiryMinutes = expiryMinutes;
    }

    public String createToken(AppUser user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirySeconds() * 1000);

        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim("username", user.getUsername())
                .claim("role", user.getRole().name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(key);
        if (user.getTeamId() != null) {
            builder.claim("teamId", user.getTeamId());
        }
        return builder.compact();
    }

    public long expirySeconds() {
        return expiryMinutes * 60;
    }
}
