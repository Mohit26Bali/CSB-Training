package com.csb.banking.entity;

/**
 * Supported account types (project description, section 6). Both follow the same transaction rules for now.
 *
 * <p>Stored on {@link Account#getAccountType()}; chosen by the client in
 * {@code dto/CreateAccountRequest}; an unsupported value is rejected with 400 by
 * {@code exception/GlobalExceptionHandler} (unreadable request body).
 */
public enum AccountType {
    SAVINGS,
    CURRENT
}
