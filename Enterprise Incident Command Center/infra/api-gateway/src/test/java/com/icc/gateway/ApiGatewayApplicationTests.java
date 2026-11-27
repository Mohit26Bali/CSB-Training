package com.icc.gateway;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "JWT_SECRET=gateway-test-secret-gateway-test-secret-12345",
        "icc.cors.allowed-origin=http://localhost:5173"
})
@AutoConfigureMockMvc
class ApiGatewayApplicationTests {

    private static final String SECRET = "gateway-test-secret-gateway-test-secret-12345";

    @Autowired
    private MockMvc mockMvc;

    private String token(String secret) {
        return Jwts.builder()
                .subject("7")
                .claim("username", "asha")
                .claim("role", "SUPPORT_ENGINEER")
                .claim("teamId", 3L)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void health_isOpenWithoutToken() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void protectedPath_withoutToken_gets401InErrorFormat() throws Exception {
        mockMvc.perform(get("/api/incidents"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(header().exists("X-Correlation-Id"));
    }

    @Test
    void forgedToken_gets401() throws Exception {
        mockMvc.perform(get("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token("some-other-secret-some-other-secret-9999")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void validToken_passesSecurityAndIsRouted() throws Exception {
        // No service is registered in this test, so the route is found but has no instance: 503.
        mockMvc.perform(get("/api/incidents")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void internalPath_isNotRouted() throws Exception {
        mockMvc.perform(get("/internal/incidents/1")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(SECRET)))
                .andExpect(status().isNotFound());
    }

    @Test
    void corsPreflight_fromTheReactOrigin_isAllowedWithoutToken() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void corsPreflight_fromAnotherOrigin_isRefused() throws Exception {
        mockMvc.perform(options("/api/incidents")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }
}
