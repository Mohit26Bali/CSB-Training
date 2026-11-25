package com.cartnest.security;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.cartnest.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 401 and 403 raised by the security filters (before any controller runs), written in the same JSON shape as every
 * other error.
 */
@Component
public class JsonSecurityErrors {

    private final ObjectMapper objectMapper;

    public JsonSecurityErrors(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AuthenticationEntryPoint unauthorized() {
        return (request, response, ex) -> write(request, response, HttpStatus.UNAUTHORIZED,
                "Please log in to continue");
    }

    public AccessDeniedHandler forbidden() {
        return (request, response, ex) -> write(request, response, HttpStatus.FORBIDDEN,
                "You do not have permission to do this");
    }

    private void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), new ApiError(status.value(), status.getReasonPhrase(),
                message, request.getRequestURI(), Map.of(), LocalDateTime.now()));
    }
}
