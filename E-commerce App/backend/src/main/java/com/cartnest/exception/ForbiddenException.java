package com.cartnest.exception;

/** The user is logged in but this action is not open to them (e.g. reviewing a product they never received). 403. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
