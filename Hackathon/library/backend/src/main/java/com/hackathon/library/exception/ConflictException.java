package com.hackathon.library.exception;

/** The request is valid but clashes with the current state (duplicate, overlap, already decided...): HTTP 409. */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
