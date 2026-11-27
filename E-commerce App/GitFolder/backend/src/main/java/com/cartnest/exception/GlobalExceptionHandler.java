package com.cartnest.exception;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.cartnest.dto.ApiError;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> notFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiError> badRequest(BadRequestException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> conflict(ConflictException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(ForbiddenException.class)
    public ResponseEntity<ApiError> forbidden(ForbiddenException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(PaymentFailedException.class)
    public ResponseEntity<ApiError> paymentFailed(PaymentFailedException ex, HttpServletRequest request) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> badCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(TooManyAttemptsException.class)
    public ResponseEntity<ApiError> tooManyAttempts(TooManyAttemptsException ex, HttpServletRequest request) {
        return build(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), request, Map.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> integrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data", request, Map.of());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> validation(BindException ex, HttpServletRequest request) {
        Map<String, String> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(FieldError::getField, this::describe, (first, second) -> first,
                        LinkedHashMap::new));
        String message = fieldErrors.entrySet().stream()
                .map(e -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message, request, fieldErrors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> unreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Request body is missing or is not valid JSON", request, Map.of());
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> typeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Invalid value '" + ex.getValue() + "' for '" + ex.getName() + "'",
                request, Map.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> missingParameter(MissingServletRequestParameterException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parameter '" + ex.getParameterName() + "' is required", request,
                Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> methodNotAllowed(HttpRequestMethodNotSupportedException ex,
                                                     HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Method " + ex.getMethod() + " is not allowed here", request,
                Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> noEndpoint(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "No such endpoint", request, Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong. Please try again later", request,
                Map.of());
    }

    private String describe(FieldError error) {
        return error.isBindingFailure() ? "invalid value '" + error.getRejectedValue() + "'"
                : String.valueOf(error.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request,
                                           Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(new ApiError(status.value(), status.getReasonPhrase(), message,
                request.getRequestURI(), fieldErrors, LocalDateTime.now()));
    }
}
