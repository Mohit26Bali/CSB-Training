package com.hackathon.helpdesk.exception;

/** A business-rule violation tied to one input field, so the UI can show it under that field. */
public class FieldValidationException extends RuntimeException {

    private final String field;

    public FieldValidationException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
