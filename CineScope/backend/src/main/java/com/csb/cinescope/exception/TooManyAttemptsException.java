package com.csb.cinescope.exception;

import org.springframework.http.HttpStatus;

/**
 * Too many failed logins for a username; further attempts are refused for a while (429). Thrown by
 * {@code service/LoginAttemptService}, which {@code service/AuthService#login} consults before checking the password.
 */
public class TooManyAttemptsException extends AppException {

    public TooManyAttemptsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
