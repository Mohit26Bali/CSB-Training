package com.csb.banking.service;

import java.math.BigDecimal;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.banking.entity.Account;
import com.csb.banking.entity.Transaction;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.TransactionRepository;

/**
 * Writes the FAILED ledger rows ("every financial operation must produce an auditable record", even a rejected one).
 *
 * <p>Called by {@link TransactionService} after {@link LedgerService} has thrown and its own transaction has already
 * rolled back. It runs in a fresh transaction of its own, so the FAILED row survives the rollback, and it never
 * touches the balance, which keeps business rule 16 intact ("a failed transaction must not change a balance").
 * Do not call it from inside the money transaction: the account row is locked there and this write would wait on it.
 */
@Service
public class TransactionAuditService {

    private static final int MAX_DESCRIPTION = 255;

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    public TransactionAuditService(AccountRepository accountRepository,
                                   TransactionRepository transactionRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public void recordFailure(String accountNumber, String counterpartyAccountNumber, TransactionType type,
                              TransactionDirection direction, BigDecimal amount, String reason) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (account == null) {
            return;
        }
        String description = ("FAILED: " + reason);
        if (description.length() > MAX_DESCRIPTION) {
            description = description.substring(0, MAX_DESCRIPTION);
        }
        transactionRepository.save(new Transaction(account, UUID.randomUUID().toString(), type, direction,
                amount, TransactionStatus.FAILED, description, counterpartyAccountNumber, account.getBalance()));
    }
}
