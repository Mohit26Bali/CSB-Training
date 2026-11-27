package com.hackathon.complaints.exception;

public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String message) {
        super(message);
    }
}
