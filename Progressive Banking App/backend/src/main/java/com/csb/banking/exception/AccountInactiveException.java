package com.csb.banking.exception;

import org.springframework.http.HttpStatus;

/**
 * The account exists but is not ACTIVE, so the operation is refused (422). Thrown by
 * {@code service/AccountService} (balance) and {@code service/LedgerService} (deposit, withdraw, transfer).
 */
public class AccountInactiveException extends BankingException {

    public AccountInactiveException(String accountNumber) {
        super(HttpStatus.UNPROCESSABLE_CONTENT, "Account " + accountNumber + " is not active");
    }
}
