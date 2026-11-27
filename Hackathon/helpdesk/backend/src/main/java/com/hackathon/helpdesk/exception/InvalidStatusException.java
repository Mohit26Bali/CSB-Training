package com.hackathon.helpdesk.exception;

public class InvalidStatusException extends RuntimeException {

    public InvalidStatusException(String message) {
        super(message);
    }
}
