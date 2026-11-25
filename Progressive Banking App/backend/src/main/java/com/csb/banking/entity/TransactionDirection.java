package com.csb.banking.entity;

/**
 * Whether a {@link Transaction} row increased (CREDIT) or decreased (DEBIT) the balance of its account.
 *
 * <p>Not named in the project description, but needed because a TRANSFER appears in the history of both accounts
 * with opposite effects. Deposit = CREDIT, withdrawal = DEBIT.
 */
public enum TransactionDirection {
    CREDIT,
    DEBIT
}
