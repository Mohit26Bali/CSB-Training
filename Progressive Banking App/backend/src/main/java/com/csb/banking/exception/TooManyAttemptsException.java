package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Too many failed logins for a username; further attempts are refused for a while (429). Thrown by
 * {@code service/LoginAttemptService}, which {@code service/AuthService#login} consults before authenticating.
 */
public class TooManyAttemptsException extends BankingException {

    public TooManyAttemptsException(String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, message);
    }
}
