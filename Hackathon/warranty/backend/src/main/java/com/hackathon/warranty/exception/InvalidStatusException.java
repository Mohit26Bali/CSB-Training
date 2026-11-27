package com.hackathon.warranty.exception;

public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String message) {
        super(message);
    }
}
