package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * No account with this number exists, or it belongs to someone else (404 in both cases, so the API does not reveal
 * which account numbers exist). Thrown by {@code service/AccountService} and {@code service/AdminAccountService}.
 */
public class AccountNotFoundException extends BankingException {

    public AccountNotFoundException(String accountNumber) {
        super(HttpStatus.NOT_FOUND, "Account not found: " + accountNumber);
    }

    /** No transaction was attempted (or it is not the caller's account): nothing to record in the ledger. */
    @Override
    public boolean isAuditable() {
        return false;
    }
}
