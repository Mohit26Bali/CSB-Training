package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Something other than an account does not exist (for example the customer profile of a user). 404.
 * Thrown by {@code service/AuthService} and {@code service/AccountService}.
 */
public class ResourceNotFoundException extends BankingException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, message);
    }
}
