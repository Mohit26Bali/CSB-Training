package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * A history filter makes no sense, e.g. "from" after "to", or minimum amount above maximum (400). Thrown by the
 * constructor of {@code dto/TransactionFilter}, i.e. while the controller builds the filter from the query string.
 */
public class InvalidFilterException extends BankingException {

    public InvalidFilterException(String message) {
        super(HttpStatus.BAD_REQUEST, message);
    }

    /** A bad query string is not a transaction attempt: nothing to record in the ledger. */
    @Override
    public boolean isAuditable() {
        return false;
    }
}
