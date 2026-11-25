package com.csb.banking.entity;

/**
 * What kind of financial operation a {@link Transaction} row records (project description, section 11).
 *
 * <p>Together with {@link TransactionDirection} it tells the history screen whether money came in or went out:
 * a TRANSFER produces one DEBIT row on the source account and one CREDIT row on the destination account.
 */
public enum TransactionType {
    DEPOSIT,
    WITHDRAWAL,
    TRANSFER
}
