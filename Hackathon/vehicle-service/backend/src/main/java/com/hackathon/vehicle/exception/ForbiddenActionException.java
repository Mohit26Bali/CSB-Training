package com.hackathon.vehicle.exception;

public class ForbiddenActionException extends RuntimeException {

    public ForbiddenActionException(String message) {
        super(message);
    }
}
