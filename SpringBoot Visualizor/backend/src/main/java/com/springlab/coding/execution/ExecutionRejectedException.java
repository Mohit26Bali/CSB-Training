package com.springlab.coding.execution;

import org.springframework.http.HttpStatus;

/** A submission that was refused before anything ran. The status is what a controller will answer with. */
public abstract class ExecutionRejectedException extends RuntimeException {

    private final HttpStatus status;

    protected ExecutionRejectedException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
