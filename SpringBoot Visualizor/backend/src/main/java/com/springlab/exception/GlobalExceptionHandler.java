package com.springlab.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Handles exceptions raised inside controllers/services. Exceptions raised in security filters
 * (missing/invalid token) never reach this class; see RestAuthenticationEntryPoint / RestAccessDeniedHandler.
 * The base class already turns standard Spring MVC exceptions (malformed JSON, 405, 415, ...) into ProblemDetail.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        // Class-level / getter-based constraints (e.g. @AssertTrue) can surface as field errors too; global ones go here.
        ex.getBindingResult().getGlobalErrors()
                .forEach(globalError -> errors.putIfAbsent(globalError.getObjectName(), globalError.getDefaultMessage()));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setTitle("Bad Request");
        problem.setProperty("errors", errors);
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicate(DuplicateResourceException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(BusinessRuleViolationException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleViolationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Conflict");
        return problem;
    }

    /** A concurrent edit of the same row (@Version mismatch detected at flush time). */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "The resource was modified by someone else. Reload it and try again");
        problem.setTitle("Conflict");
        return problem;
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Not Found");
        return problem;
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Bad Request");
        return problem;
    }

    /** Failed login (bad credentials, disabled account, ...). One generic message avoids user enumeration. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password");
        problem.setTitle("Unauthorized");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).header(HttpHeaders.WWW_AUTHENTICATE, "Bearer").body(problem);
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) throws Exception {
        // A catch-all @ExceptionHandler(Exception.class) would swallow @PreAuthorize denials and turn them into 500s.
        // Rethrow so ExceptionTranslationFilter can map them to 401 (anonymous) or 403 (authenticated).
        if (ex instanceof AccessDeniedException) {
            throw ex;
        }
        log.error("Unhandled exception", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal Server Error");
        return problem;
    }

    /** A coding submission refused before anything ran: 400 invalid source, 409 already running, 429 runner busy, 503 not available. */
    @ExceptionHandler(com.springlab.coding.execution.ExecutionRejectedException.class)
    public ProblemDetail handleExecutionRejected(com.springlab.coding.execution.ExecutionRejectedException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(ex.status(), ex.getMessage());
        problem.setTitle(ex.status().getReasonPhrase());
        return problem;
    }
}
