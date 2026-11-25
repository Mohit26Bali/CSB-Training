package com.cartnest.exception;

/** A requested category, product, order... does not exist. Answered with 404. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
