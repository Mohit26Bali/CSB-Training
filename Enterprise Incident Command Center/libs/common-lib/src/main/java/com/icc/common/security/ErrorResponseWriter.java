package com.icc.common.security;

import java.io.IOException;

import org.slf4j.MDC;
import org.springframework.http.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.icc.common.correlation.CorrelationIds;
import com.icc.common.error.ErrorCode;
import com.icc.common.error.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class ErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response,
                      ErrorCode code, String message) throws IOException {
        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI(),
                MDC.get(CorrelationIds.MDC_KEY));
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), body);
    }
}
