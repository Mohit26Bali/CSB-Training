package com.csb.banking.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The single JSON shape for every error the API returns.
 *
 * <p>Produced in two places that must stay in sync: {@code exception/GlobalExceptionHandler} (errors raised inside
 * controllers and services) and {@code security/JsonErrorWriter} (401 / 403 raised by the security filters before a
 * controller runs). {@code fieldErrors} maps a request field to its validation message so the React forms can show
 * the message under the right input; it is empty for non-validation errors.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        Map<String, String> fieldErrors,
        LocalDateTime timestamp) {

    public static ErrorResponse of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, message, fieldErrors, LocalDateTime.now());
    }
}
