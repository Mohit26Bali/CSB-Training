package com.csb.cinescope.security;

import java.io.IOException;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Runs once per request and authenticates the caller from the {@code Authorization: Bearer <token>} header.
 *
 * <p>Registered in {@code config/SecurityConfig}; validates the token with {@link JwtService}; loads the user with
 * {@code service/CustomUserDetailsService}. A valid token puts the user into the SecurityContext, which is where controllers'
 * {@code Authentication} parameters come from. A missing or invalid token leaves the request anonymous: public endpoints
 * still work, and protected ones are answered 401 by {@link RestAuthenticationEntryPoint}.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;

    public JwtAuthenticationFilter(JwtService jwtService, UserDetailsService userDetailsService) {
        this.jwtService = jwtService;
        this.userDetailsService = userDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(PREFIX) && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.TokenClaims claims = jwtService.verify(header.substring(PREFIX.length()).trim());
                UserDetails user = userDetailsService.loadUserByUsername(claims.username());
                if (user.isEnabled() && isCurrent(user, claims)) {
                    UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException | AuthenticationException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }

    /** False for a token issued before the account's last password change, role change or disabling. */
    private static boolean isCurrent(UserDetails user, JwtService.TokenClaims claims) {
        return !(user instanceof CineScopeUserDetails details) || details.getTokenVersion() == claims.tokenVersion();
    }
}
