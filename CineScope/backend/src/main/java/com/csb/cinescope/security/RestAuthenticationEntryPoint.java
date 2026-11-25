package com.csb.cinescope.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Answers 401 (JSON) when a protected URL is called without a valid token. Plugged in by {@code config/SecurityConfig}. */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.UNAUTHORIZED, "Please log in to continue");
    }
}
