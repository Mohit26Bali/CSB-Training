package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * The {@code Idempotency-Key} header is present but malformed (400): it must be 8 to 64 characters of letters,
 * digits, '-' or '_' (a UUID is ideal). Thrown by {@code service/IdempotencyService}.
 */
public class InvalidIdempotencyKeyException extends BankingException {

    public InvalidIdempotencyKeyException() {
        super(HttpStatus.BAD_REQUEST,
                "Idempotency-Key must be 8 to 64 characters: letters, digits, '-' or '_' (a UUID is ideal)");
    }

    /** No transaction was attempted (or it is not the caller's account): nothing to record in the ledger. */
    @Override
    public boolean isAuditable() {
        return false;
    }
}
