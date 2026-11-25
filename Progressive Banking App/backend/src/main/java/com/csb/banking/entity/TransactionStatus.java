package com.csb.banking.entity;

/**
 * Outcome of a {@link Transaction} (project description, section 11). PENDING is a later-phase addition.
 *
 * <p>FAILED rows are the audit trail of rejected operations; they never change any balance.
 */
public enum TransactionStatus {
    SUCCESS,
    FAILED
}
