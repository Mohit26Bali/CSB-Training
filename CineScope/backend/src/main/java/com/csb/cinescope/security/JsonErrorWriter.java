package com.csb.cinescope.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/**
 * Writes the JSON error body for failures that happen inside the security filters, before any controller (and therefore
 * before {@code exception/GlobalExceptionHandler}) can run. Used by {@link RestAuthenticationEntryPoint} and
 * {@link RestAccessDeniedHandler}; the output has the same fields as {@code dto/ErrorResponse}.
 */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String safeMessage = message.replace("\\", "\\\\").replace("\"", "\\\"");
        response.getWriter().write(String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"fieldErrors\":{},\"timestamp\":\"%s\"}",
                status.value(), status.getReasonPhrase(), safeMessage, LocalDateTime.now()));
    }
}
