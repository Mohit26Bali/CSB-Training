package com.hackathon.visitors.exception;

public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
