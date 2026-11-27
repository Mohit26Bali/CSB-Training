package com.springlab.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

/** 401: no credentials, or credentials (Bearer token) that failed validation. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemDetailWriter writer;
    // Sets the RFC 6750 "WWW-Authenticate: Bearer ..." header (with error=invalid_token when applicable).
    private final BearerTokenAuthenticationEntryPoint bearerEntryPoint = new BearerTokenAuthenticationEntryPoint();

    public RestAuthenticationEntryPoint(ProblemDetailWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        bearerEntryPoint.commence(request, response, authException);
        // Generic detail on purpose: do not leak why a token was rejected.
        writer.write(request, response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                "Authentication is required and must be a valid Bearer token");
    }
}
