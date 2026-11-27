package com.hackathon.procurement.security;

import java.io.IOException;
import java.time.LocalDateTime;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import jakarta.servlet.http.HttpServletResponse;

/** Writes the same JSON shape as ErrorResponse for failures raised before a controller runs. */
final class JsonErrorWriter {

    private JsonErrorWriter() {
    }

    static void write(HttpServletResponse response, HttpStatus status, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(String.format(
                "{\"status\":%d,\"error\":\"%s\",\"message\":\"%s\",\"timestamp\":\"%s\"}",
                status.value(), status.getReasonPhrase(), message.replace("\"", "'"), LocalDateTime.now()));
    }
}
