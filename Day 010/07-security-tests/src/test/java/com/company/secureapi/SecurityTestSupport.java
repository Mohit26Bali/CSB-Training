package com.company.secureapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

/**
 * Shared set-up: the whole application (@SpringBootTest) against the REAL MySQL
 * test database (hr_tests_day10_test, rebuilt by Flyway at every run), with
 * MockMvc sending requests through the full Spring Security filter chain.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class SecurityTestSupport {

    static final Map<String, String> PASSWORDS = Map.of("admin", "Admin@123", "hana", "Hana@123",
            "manoj", "Manoj@123", "asha", "Asha@123", "ravi", "Ravi@123");

    private static final Map<String, String> TOKENS = new HashMap<>();

    @Autowired
    MockMvc mockMvc;

    @Value("${portal.jwt.secret}")
    String jwtSecret;

    /** "Bearer <JWT>" from a real login (cached: one login per user per test run). */
    String bearer(String user) throws Exception {
        if (!TOKENS.containsKey(user)) {
            String body = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, PASSWORDS.get(user))))
                    .andReturn().getResponse().getContentAsString();
            TOKENS.put(user, JsonPath.read(body, "$.accessToken"));
        }
        return "Bearer " + TOKENS.get(user);
    }

    /** A token signed with the real key but already expired (for the expired-JWT test). */
    String expiredBearer(String user, String role) {
        Instant past = Instant.now().minusSeconds(3600);
        return "Bearer " + Jwts.builder().issuer("hr-portal").subject(user)
                .issuedAt(Date.from(past)).expiration(Date.from(past.plusSeconds(900)))
                .claim("authorities", List.of("ROLE_" + role))
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret)), Jwts.SIG.HS256)
                .compact();
    }
}
