package com.springlab.exception;

/** A well-formed request that the current state of the system does not allow (mapped to 409). */
public class BusinessRuleViolationException extends RuntimeException {

    public BusinessRuleViolationException(String message) {
        super(message);
    }
}
