package com.csb.cinescope.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * The one JSON shape of every error, produced by {@code exception/GlobalExceptionHandler} (and, for 401 / 403 raised
 * before a controller runs, by {@code security/JsonErrorWriter}, which writes the same fields). {@code fieldErrors} maps a
 * request field to its validation message so the React forms can show it under the right input; it is empty otherwise.
 */
public record ErrorResponse(int status, String error, String message, Map<String, String> fieldErrors, LocalDateTime timestamp) {

    public static ErrorResponse of(int status, String error, String message) {
        return new ErrorResponse(status, error, message, Map.of(), LocalDateTime.now());
    }

    public static ErrorResponse of(int status, String error, String message, Map<String, String> fieldErrors) {
        return new ErrorResponse(status, error, message, fieldErrors, LocalDateTime.now());
    }
}
