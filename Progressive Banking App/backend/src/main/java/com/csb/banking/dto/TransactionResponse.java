package com.csb.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;

/**
 * One ledger line as the client sees it: the answer of deposit / withdraw and an element of the history page.
 *
 * <p>Built by {@code mapper/TransactionMapper} from an {@code entity/Transaction}. {@code balanceAfter} lets the UI
 * show the new balance without a second request.
 */
public record TransactionResponse(
        String transactionRef,
        TransactionType type,
        TransactionDirection direction,
        BigDecimal amount,
        TransactionStatus status,
        String description,
        String counterpartyAccountNumber,
        BigDecimal balanceAfter,
        LocalDateTime createdAt) {
}
