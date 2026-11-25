package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class of every expected business failure.
 *
 * <p>Subclasses pick the HTTP status once, in their constructor; {@link GlobalExceptionHandler} reads it through
 * {@link #getStatus()}. Adding a new business rule therefore means adding a small subclass and throwing it from a
 * service, with no change to the handler.
 */
public abstract class BankingException extends RuntimeException {

    private final HttpStatus status;

    protected BankingException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Whether {@code service/TransactionService} should write a FAILED ledger row when this exception rejects a
     * money request. True for real transaction rejections (insufficient balance, inactive account, ...); false for
     * errors where no transaction was ever attempted or the account is not the caller's.
     */
    public boolean isAuditable() {
        return true;
    }
}
