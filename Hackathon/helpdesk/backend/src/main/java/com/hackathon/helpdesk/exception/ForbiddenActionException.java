package com.hackathon.helpdesk.exception;

public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
