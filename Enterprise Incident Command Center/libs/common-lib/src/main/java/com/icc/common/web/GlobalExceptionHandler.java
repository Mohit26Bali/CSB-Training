package com.icc.common.web;

import com.icc.common.correlation.CorrelationIds;
import com.icc.common.error.ApiException;
import com.icc.common.error.ErrorCode;
import com.icc.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApi(ApiException ex, HttpServletRequest request) {
        return build(ex.getErrorCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex,
                                                          HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + " " + error.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return build(ErrorCode.VALIDATION_FAILED, message, request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex,
                                                          HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, "Request body is missing or not valid JSON", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex,
                                                            HttpServletRequest request) {
        return build(ErrorCode.FORBIDDEN, "You are not allowed to do this", request);
    }

    // The gateway throws this (status 503) when no instance of a service is registered.
    @ExceptionHandler(HttpServerErrorException.class)
    public ResponseEntity<ErrorResponse> handleServerError(HttpServerErrorException ex,
                                                           HttpServletRequest request) {
        if (ex.getStatusCode().value() == 503) {
            log.warn("Service unavailable on {}: {}", request.getRequestURI(), ex.getMessage());
            return build(ErrorCode.SERVICE_UNAVAILABLE, "The service is temporarily unavailable", request);
        }
        log.error("Server error from a called service on {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "Unexpected error", request);
    }

    // The gateway could not connect to a service that is still in its list (connection refused, timeout).
    // For the caller this is the same as "no instance": 503, not a 500.
    @ExceptionHandler(ResourceAccessException.class)
    public ResponseEntity<ErrorResponse> handleConnectionFailure(ResourceAccessException ex,
                                                                 HttpServletRequest request) {
        log.warn("Could not reach a service on {}: {}", request.getRequestURI(), ex.getMessage());
        return build(ErrorCode.SERVICE_UNAVAILABLE, "The service is temporarily unavailable", request);
    }

    // A bad query parameter or path variable: wrong type (page=abc), missing, or out of range (size=0).
    @ExceptionHandler({HandlerMethodValidationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponse> handleBadParameter(Exception ex, HttpServletRequest request) {
        return build(ErrorCode.VALIDATION_FAILED, "Invalid or missing request parameter", request);
    }

    // An unknown URL. Without this, Spring forwards to /error, where the login is lost and the caller sees a 401.
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(ErrorCode.NOT_FOUND, "No endpoint " + request.getRequestURI(), request);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(RuntimeException ex, HttpServletRequest request) {
        log.error("Unexpected error on {}", request.getRequestURI(), ex);
        return build(ErrorCode.INTERNAL_ERROR, "Unexpected error", request);
    }

    private ResponseEntity<ErrorResponse> build(ErrorCode code, String message, HttpServletRequest request) {
        ErrorResponse body = ErrorResponse.of(code, message, request.getRequestURI(),
                MDC.get(CorrelationIds.MDC_KEY));
        return ResponseEntity.status(code.getStatus()).body(body);
    }
}
