package com.icc.common.feign;

import com.icc.common.correlation.CorrelationIds;
import com.icc.common.error.ErrorCode;
import com.icc.common.error.ErrorResponse;
import feign.FeignException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.cloud.client.circuitbreaker.NoFallbackAvailableException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a failed call to another service into our error format.
 * It runs before GlobalExceptionHandler, whose catch-all would otherwise turn it into a 500.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FeignExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FeignExceptionHandler.class);

    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ErrorResponse> handle(FeignException ex, HttpServletRequest request) {
        log.warn("A call to another service failed on {}: status {} {}", request.getRequestURI(),
                ex.status(), ex.getMessage());

        ErrorCode code = switch (ex.status()) {
            case 400 -> ErrorCode.VALIDATION_FAILED;
            case 401 -> ErrorCode.UNAUTHORIZED;
            case 403 -> ErrorCode.FORBIDDEN;
            case 404 -> ErrorCode.NOT_FOUND;
            case 409 -> ErrorCode.CONFLICT;
            default -> ErrorCode.SERVICE_UNAVAILABLE; // 5xx, timeouts and connection failures (status -1)
        };
        String message = switch (code) {
            case VALIDATION_FAILED -> "A dependent service rejected the request";
            case UNAUTHORIZED, FORBIDDEN -> "A dependent service refused the request";
            case NOT_FOUND -> "A referenced record was not found";
            case CONFLICT -> "A dependent service reported a conflict";
            default -> "A dependent service is temporarily unavailable";
        };

        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI(),
                MDC.get(CorrelationIds.MDC_KEY));
        return ResponseEntity.status(code.getStatus()).body(body);
    }

    /**
     * The circuit breaker of a Feign client is open: it refuses the call at once, without asking the other
     * service. Spring wraps that refusal in NoFallbackAvailableException. For the caller it is the same
     * situation as a dead service, so it is a 503 in our format (and not a 500).
     */
    @ExceptionHandler(NoFallbackAvailableException.class)
    public ResponseEntity<ErrorResponse> handleOpenCircuit(NoFallbackAvailableException ex,
                                                           HttpServletRequest request) {
        log.warn("Circuit breaker is open, call refused on {}: {}", request.getRequestURI(),
                ex.getCause() == null ? ex.getMessage() : ex.getCause().getMessage());
        ErrorResponse body = ErrorResponse.of(ErrorCode.SERVICE_UNAVAILABLE,
                "A dependent service is temporarily unavailable", request.getRequestURI(),
                MDC.get(CorrelationIds.MDC_KEY));
        return ResponseEntity.status(ErrorCode.SERVICE_UNAVAILABLE.getStatus()).body(body);
    }
}
