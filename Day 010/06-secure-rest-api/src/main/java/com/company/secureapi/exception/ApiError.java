package com.company.secureapi.exception;

import java.time.Instant;

import org.slf4j.MDC;
import org.springframework.http.HttpStatus;

import com.company.secureapi.config.CorrelationIdFilter;

/**
 * THE error contract: every error of the API, security or business, has this shape.
 *
 *   code           machine-readable; tells security errors (AUTH_*, ACCESS_DENIED)
 *                  apart from controller errors (VALIDATION_FAILED, EMPLOYEE_NOT_FOUND ...)
 *   correlationId  the same value as the X-Correlation-Id response header and in the
 *                  server log, so a client's error report can be found in the logs
 */
public record ApiError(Instant timestamp, int status, String error, String code, String message,
                       String path, String correlationId) {

    public static ApiError of(HttpStatus status, ErrorCode code, String message, String path) {
        return new ApiError(Instant.now(), status.value(), status.getReasonPhrase(), code.name(), message, path,
                MDC.get(CorrelationIdFilter.MDC_KEY));
    }
}
