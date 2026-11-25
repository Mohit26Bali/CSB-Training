package com.csb.banking.mapper;

import org.springframework.stereotype.Component;

import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.entity.Transaction;

/**
 * Builds the {@link TransactionResponse} for a {@link Transaction}.
 *
 * <p>Used by {@code service/LedgerService} (answer of deposit / withdraw) and {@code service/TransactionService}
 * (history pages).
 */
@Component
public class TransactionMapper {

    public TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getTransactionRef(),
                transaction.getType(),
                transaction.getDirection(),
                transaction.getAmount(),
                transaction.getStatus(),
                transaction.getDescription(),
                transaction.getCounterpartyAccountNumber(),
                transaction.getBalanceAfter(),
                transaction.getCreatedAt());
    }
}
