package com.company.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.company.jwt.config.JwtProperties;
import com.jayway.jsonpath.JsonPath;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Base class: the full application against the MySQL test database, plus
 * helpers to log in and to HAND-BUILD tokens (expired, wrong key, tampered,
 * unsigned ...) with jjwt, the way an attacker or a buggy client might.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class JwtTestSupport {

    static final String ASHA_HASH = "$2a$10$clL4DrDv/4T6xux8kdP1AOYJzIoz.puLOYTx9ATyati.njDo/.Juu";  // Asha@123

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JwtProperties properties;

    @Autowired
    JdbcTemplate jdbc;

    record Tokens(String accessToken, String refreshToken) {
    }

    Tokens login(String username, String password) throws Exception {
        String json = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return new Tokens(JsonPath.read(json, "$.accessToken"), JsonPath.read(json, "$.refreshToken"));
    }

    static String bearer(String token) {
        return "Bearer " + token;
    }

    /** A fresh USER account (password Asha@123) that a test may change, disable or delete. */
    String createUser() {
        String username = "t" + UUID.randomUUID().toString().substring(0, 8);
        jdbc.update("INSERT INTO users (username, email, full_name, password_hash, enabled, created_at) "
                + "VALUES (?, ?, 'Test User', ?, TRUE, NOW())", username, username + "@company.com", ASHA_HASH);
        jdbc.update("INSERT INTO user_roles (user_id, role_id) SELECT u.id, r.id FROM users u, roles r "
                + "WHERE u.username = ? AND r.name = 'USER'", username);
        return username;
    }

    Long idOf(String username) {
        return jdbc.queryForObject("SELECT id FROM users WHERE username = ?", Long.class, username);
    }

    SecretKey key(String keyId) {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(properties.keys().get(keyId)));
    }

    /** A correctly signed token with the given details. */
    String signedToken(String keyId, SecretKey key, String issuer, String subject, List<String> authorities,
                       Instant issuedAt, Instant expiresAt) {
        return Jwts.builder()
                .header().keyId(keyId).and()
                .issuer(issuer).subject(subject).id(UUID.randomUUID().toString())
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt))
                .claim("authorities", authorities)
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    String validToken(String subject, List<String> authorities) {
        Instant now = Instant.now();
        return signedToken(properties.activeKeyId(), key(properties.activeKeyId()), properties.issuer(),
                subject, authorities, now, now.plusSeconds(600));
    }

    /** Replaces the payload (middle part) but keeps the original signature: a forged token. */
    static String withPayload(String token, String newPayloadJson) {
        String[] parts = token.split("\\.");
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(newPayloadJson.getBytes(StandardCharsets.UTF_8));
        return parts[0] + "." + payload + "." + parts[2];
    }

    static String decodePart(String token, int index) {
        return new String(Base64.getUrlDecoder().decode(token.split("\\.")[index]), StandardCharsets.UTF_8);
    }
}
