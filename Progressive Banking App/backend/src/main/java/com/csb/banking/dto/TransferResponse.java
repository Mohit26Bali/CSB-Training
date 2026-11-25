package com.csb.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.csb.banking.entity.TransactionStatus;

/**
 * Answer of {@code POST /api/transfers}: the shared transaction reference of both ledger rows and the source
 * account's new balance. Built by {@code service/LedgerService#transfer}.
 */
public record TransferResponse(
        String transactionRef,
        String fromAccount,
        String toAccount,
        BigDecimal amount,
        TransactionStatus status,
        BigDecimal fromAccountBalanceAfter,
        LocalDateTime createdAt) {
}
