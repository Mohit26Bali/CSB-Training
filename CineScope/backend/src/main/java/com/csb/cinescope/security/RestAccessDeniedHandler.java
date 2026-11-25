package com.csb.cinescope.security;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Answers 403 (JSON) when a logged-in user calls something they may not. Plugged in by {@code config/SecurityConfig}. */
@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        JsonErrorWriter.write(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action");
    }
}
