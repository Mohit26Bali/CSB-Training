package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * An amount is zero or negative where a positive one is required (400). Request-level checks already catch this in
 * {@code dto/}; this exception is the service-level safety net used by {@code service/LedgerService}.
 */
public class InvalidAmountException extends BankingException {

    public InvalidAmountException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }
}
