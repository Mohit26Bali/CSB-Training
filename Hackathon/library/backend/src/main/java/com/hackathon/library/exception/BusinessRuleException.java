package com.hackathon.library.exception;

/** A business rule that is not about one input field is broken (inactive item, past date...): HTTP 400. */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
