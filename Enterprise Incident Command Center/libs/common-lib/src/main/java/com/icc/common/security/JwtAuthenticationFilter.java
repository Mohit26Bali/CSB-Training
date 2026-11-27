package com.icc.common.security;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import com.icc.common.error.ErrorCode;
import com.icc.common.jwt.InvalidTokenException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.JwtVerifier;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER = "Bearer ";

    private final JwtVerifier verifier;
    private final ErrorResponseWriter errorWriter;

    public JwtAuthenticationFilter(JwtVerifier verifier, ErrorResponseWriter errorWriter) {
        this.verifier = verifier;
        this.errorWriter = errorWriter;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith(BEARER)) {
            chain.doFilter(request, response);
            return;
        }

        try {
            JwtClaims claims = verifier.verify(header.substring(BEARER.length()));
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    claims, null, List.of(new SimpleGrantedAuthority("ROLE_" + claims.role().name())));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (InvalidTokenException e) {
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, ErrorCode.UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        chain.doFilter(request, response);
    }
}
