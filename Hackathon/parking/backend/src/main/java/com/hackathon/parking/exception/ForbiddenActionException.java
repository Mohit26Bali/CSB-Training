package com.hackathon.parking.exception;

public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
