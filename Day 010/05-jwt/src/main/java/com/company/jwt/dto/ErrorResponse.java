package com.company.jwt.dto;

import java.time.Instant;

/** The one JSON error body of the API (400 / 401 / 403 / 404 / 409). */
public record ErrorResponse(Instant timestamp,
                            int status,
                            String error,
                            String message,
                            String path) {
}
