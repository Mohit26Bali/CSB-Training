package com.cartnest.exception;

/** The request is well-formed but its values do not make sense together. Answered with 400. */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
