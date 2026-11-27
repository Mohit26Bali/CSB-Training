package com.icc.common.error;

import java.time.Instant;

public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String correlationId) {

    public static ErrorResponse of(ErrorCode code, String message, String path, String correlationId) {
        return new ErrorResponse(Instant.now(), code.getStatus().value(), code.name(), message, path, correlationId);
    }
}
