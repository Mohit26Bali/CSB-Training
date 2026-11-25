package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * An {@code Idempotency-Key} that was already used for a DIFFERENT request (422). One key stands for one user action;
 * a new action needs a new key. Thrown by {@code service/IdempotencyService}.
 */
public class IdempotencyConflictException extends BankingException {

    public IdempotencyConflictException() {
        super(HttpStatus.UNPROCESSABLE_CONTENT,
                "This Idempotency-Key was already used for a different request. Use a new key for a new transaction");
    }

    /** No transaction was attempted (or it is not the caller's account): nothing to record in the ledger. */
    @Override
    public boolean isAuditable() {
        return false;
    }
}
