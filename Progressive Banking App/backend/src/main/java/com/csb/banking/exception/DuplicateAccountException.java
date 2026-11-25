package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * An account number could not be made unique (409). Thrown by {@code service/AccountService} when the database's
 * unique constraint on {@code accounts.account_number} fires despite the pre-check in
 * {@code service/AccountNumberGenerator} (two requests racing for the same number).
 */
public class DuplicateAccountException extends BankingException {

    public DuplicateAccountException(String message) {
        super(HttpStatus.CONFLICT, message);
    }
}
