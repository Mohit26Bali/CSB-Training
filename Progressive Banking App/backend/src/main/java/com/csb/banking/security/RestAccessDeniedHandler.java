package com.csb.banking.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Answers 403 (JSON) when a logged-in user calls a URL their role may not use, e.g. a CUSTOMER calling
 * {@code /api/admin/**}. Plugged into the filter chain by {@code config/SecurityConfig}; writes through
 * {@link JsonErrorWriter}. Method-level refusals ({@code @PreAuthorize}) are handled by
 * {@code exception/GlobalExceptionHandler} instead.
 */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.FORBIDDEN,
                "You do not have permission to perform this action");
    }
}
