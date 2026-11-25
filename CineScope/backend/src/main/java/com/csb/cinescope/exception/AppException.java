package com.csb.cinescope.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class of every expected failure in the application (catalogue, accounts, watchlist). Subclasses pick the HTTP status once;
 * {@link GlobalExceptionHandler} reads it, so adding a new kind of failure needs no change to the handler.
 */
public abstract class AppException extends RuntimeException {

    private final HttpStatus status;

    protected AppException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
