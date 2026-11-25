package com.cartnest.exception;

/** Too many failed logins for one email; it is locked for a while. Answered with 429. */
public class TooManyAttemptsException extends RuntimeException {

    public TooManyAttemptsException(String message) {
        super(message);
    }
}
