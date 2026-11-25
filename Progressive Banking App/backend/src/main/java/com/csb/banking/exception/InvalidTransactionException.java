package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * A transaction breaks a rule that is not covered by a more specific exception, e.g. transferring to the same
 * account (422). Thrown by the transfer operation of {@code service/LedgerService}.
 */
public class InvalidTransactionException extends BankingException {

    public InvalidTransactionException(String message) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, message);
    }
}
