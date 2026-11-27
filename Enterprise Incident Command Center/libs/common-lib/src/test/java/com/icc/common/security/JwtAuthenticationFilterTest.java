package com.icc.common.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.JwtVerifier;
import com.icc.common.jwt.Role;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtAuthenticationFilterTest {

    private static final String SECRET = "test-secret-test-secret-test-secret-1234";

    private final JwtVerifier verifier = new JwtVerifier(SECRET);
    private final ErrorResponseWriter writer = new ErrorResponseWriter(new ObjectMapper().findAndRegisterModules());
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(verifier, writer);

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    private String token(String role) {
        return Jwts.builder()
                .subject("7")
                .claim("username", "asha")
                .claim("role", role)
                .claim("teamId", 3L)
                .expiration(new Date(System.currentTimeMillis() + 3_600_000))
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();
    }

    @Test
    void noHeader_passesThroughWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void nonBearerHeader_passesThroughWithoutLogin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Basic abc123");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void validToken_logsTheUserIn() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token("ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertNotNull(chain.getRequest());
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        JwtClaims principal = (JwtClaims) authentication.getPrincipal();
        assertEquals(Role.ADMIN, principal.role());
        assertEquals(7L, principal.userId());
        assertEquals(3L, principal.teamId());
        assertEquals(List.of("ROLE_ADMIN"),
                authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
    }

    @Test
    void invalidToken_gets401AndStopsTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer not-a-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(401, response.getStatus());
        assertTrue(response.getContentAsString().contains("UNAUTHORIZED"));
        assertNull(chain.getRequest());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
}
