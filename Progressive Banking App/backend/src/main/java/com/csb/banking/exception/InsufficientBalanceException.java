package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * A withdrawal or transfer would take the balance below zero (422). Thrown by
 * {@code service/LedgerService}; the transaction rolls back, so the balance stays untouched.
 */
public class InsufficientBalanceException extends BankingException {

    public InsufficientBalanceException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
