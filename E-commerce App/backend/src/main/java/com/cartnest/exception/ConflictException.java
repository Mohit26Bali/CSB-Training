package com.cartnest.exception;

/** A business rule blocks the request: email already registered, not enough stock... Answered with 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
