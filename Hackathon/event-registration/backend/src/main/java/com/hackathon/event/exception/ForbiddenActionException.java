package com.hackathon.event.exception;

public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
