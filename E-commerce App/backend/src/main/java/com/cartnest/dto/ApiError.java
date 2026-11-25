package com.cartnest.dto;

import java.time.LocalDateTime;
import java.util.Map;

/** The body of every error response. fieldErrors names each invalid input (empty when not a validation error). */
public record ApiError(int status, String error, String message, String path, Map<String, String> fieldErrors,
                       LocalDateTime timestamp) {
}
