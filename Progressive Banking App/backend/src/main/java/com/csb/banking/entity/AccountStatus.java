package com.csb.banking.entity;

/**
 * Lifecycle state of an {@link Account} (project description, section 5).
 *
 * <p>Business rule: only {@code ACTIVE} accounts may take part in financial operations or show a balance.
 * {@code service/AccountService} enforces it (throwing {@code AccountInactiveException}); an administrator
 * changes the status through {@code service/AdminAccountService}.
 */
public enum AccountStatus {
    ACTIVE,
    INACTIVE,
    BLOCKED
}
