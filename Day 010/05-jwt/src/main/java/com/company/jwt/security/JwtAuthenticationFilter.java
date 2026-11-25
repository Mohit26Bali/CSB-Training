package com.company.jwt.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reads "Authorization: Bearer <token>" on every request.
 *
 *   no Authorization header   -> do nothing; the request continues as ANONYMOUS
 *                                (public endpoints work, protected ones get 401 later)
 *   valid token               -> put an authenticated token into the SecurityContext,
 *                                with the authorities FROM THE TOKEN (no database query)
 *   invalid token             -> stop here with 401 and the reason (expired, bad signature ...)
 *
 * It runs once per request (OncePerRequestFilter) and is placed before Spring's
 * own login filters in SecurityConfig. It is created there with "new", not as a
 * @Component: a Filter bean would ALSO be registered by Spring Boot as a plain
 * servlet filter and run twice.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final RevokedAccessTokens revokedTokens;
    private final AuthenticationEntryPoint entryPoint;

    public JwtAuthenticationFilter(JwtService jwtService, RevokedAccessTokens revokedTokens,
                                   AuthenticationEntryPoint entryPoint) {
        this.jwtService = jwtService;
        this.revokedTokens = revokedTokens;
        this.entryPoint = entryPoint;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            chain.doFilter(request, response);
            return;
        }
        try {
            if (!header.startsWith(BEARER_PREFIX)) {
                throw new InvalidBearerTokenException("Authorization header must be: Bearer <token>");
            }
            Claims claims = jwtService.verify(header.substring(BEARER_PREFIX.length()).trim());
            if (claims.getId() != null && revokedTokens.isRevoked(claims.getId())) {
                throw new InvalidBearerTokenException("Token has been revoked");
            }

            List<SimpleGrantedAuthority> authorities = JwtService.authoritiesOf(claims).stream()
                    .map(SimpleGrantedAuthority::new)
                    .toList();
            AuthenticatedUser principal = new AuthenticatedUser(
                    claims.getSubject(), claims.getId(), claims.getExpiration().toInstant());

            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
            SecurityContextHolder.setContext(context);
        } catch (InvalidBearerTokenException e) {
            SecurityContextHolder.clearContext();
            entryPoint.commence(request, response, e);                 // 401 JSON, the chain stops here
            return;
        }
        chain.doFilter(request, response);
    }
}
