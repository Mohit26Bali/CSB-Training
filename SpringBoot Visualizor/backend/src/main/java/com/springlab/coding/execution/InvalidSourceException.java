package com.springlab.coding.execution;

import org.springframework.http.HttpStatus;

/** The source cannot be queued: empty, too large, not valid UTF-8, or containing a NUL character (400). */
public class InvalidSourceException extends ExecutionRejectedException {

    public InvalidSourceException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
