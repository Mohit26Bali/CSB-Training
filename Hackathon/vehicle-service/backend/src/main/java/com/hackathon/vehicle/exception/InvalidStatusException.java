package com.hackathon.vehicle.exception;

public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String message) {
        super(message);
    }
}
