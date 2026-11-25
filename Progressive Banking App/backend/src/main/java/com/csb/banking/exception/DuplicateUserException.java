package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Registration with a username or email that is already taken (409). Thrown by
 * {@code service/AuthService#register}.
 */
public class DuplicateUserException extends BankingException {

    public DuplicateUserException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
