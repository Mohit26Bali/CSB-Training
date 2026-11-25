package com.csb.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.dto.TransferResponse;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.Transaction;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.AccountInactiveException;
import com.csb.banking.exception.AccountNotFoundException;
import com.csb.banking.exception.InsufficientBalanceException;
import com.csb.banking.exception.InvalidAmountException;
import com.csb.banking.exception.InvalidTransactionException;
import com.csb.banking.mapper.TransactionMapper;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.TransactionRepository;

/**
 * The transactional core that really moves money: validates the rules, changes balances and writes the ledger.
 * Each public method is ONE database transaction - either every change is committed or none is, so a failed
 * operation can never leave a half-changed balance (business rule 16).
 *
 * <p>Connections: called only by {@link TransactionService}, which wraps it with authorization and with the recording
 * of FAILED attempts. That separation is deliberate: when a method here throws, its transaction rolls back and the
 * row locks are released; only then can {@link TransactionAuditService} write the FAILED row in a new transaction
 * without waiting on a lock held by this one.
 *
 * <p>Concurrency: every account involved is loaded with {@code AccountRepository#findByAccountNumberForUpdate}
 * (row lock), so parallel requests on one account are serialized. A transfer locks its two accounts in ascending
 * account-number order, whichever is the source, so A-to-B and B-to-A running together cannot deadlock.
 * READ_COMMITTED makes the balance read after the lock wait see the other request's committed result.
 *
 * <p>Rule order follows the project description: account exists -> belongs to caller -> ACTIVE -> amount > 0 ->
 * sufficient balance.
 */
@Service
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountAccessPolicy accessPolicy;
    private final TransactionMapper transactionMapper;

    public LedgerService(AccountRepository accountRepository, TransactionRepository transactionRepository,
                         AccountAccessPolicy accessPolicy, TransactionMapper transactionMapper) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accessPolicy = accessPolicy;
        this.transactionMapper = transactionMapper;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse deposit(String accountNumber, BigDecimal rawAmount, String description,
                                       Authentication authentication) {
        Account account = lock(accountNumber);
        accessPolicy.requireOwner(account, authentication);
        requireActive(account);
        BigDecimal amount = requirePositive(rawAmount);

        account.credit(amount);
        Transaction entry = transactionRepository.save(new Transaction(account, newRef(),
                TransactionType.DEPOSIT, TransactionDirection.CREDIT, amount, TransactionStatus.SUCCESS,
                describe(description, "Deposit"), null, account.getBalance()));
        return transactionMapper.toResponse(entry);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionResponse withdraw(String accountNumber, BigDecimal rawAmount, String description,
                                        Authentication authentication) {
        Account account = lock(accountNumber);
        accessPolicy.requireOwner(account, authentication);
        requireActive(account);
        BigDecimal amount = requirePositive(rawAmount);
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Transaction Failed: Insufficient balance.");
        }

        account.debit(amount);
        Transaction entry = transactionRepository.save(new Transaction(account, newRef(),
                TransactionType.WITHDRAWAL, TransactionDirection.DEBIT, amount, TransactionStatus.SUCCESS,
                describe(description, "Withdrawal"), null, account.getBalance()));
        return transactionMapper.toResponse(entry);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransferResponse transfer(String fromNumber, String toNumber, BigDecimal rawAmount,
                                     String description, Authentication authentication) {
        Account source;
        Account destination;
        if (fromNumber.equals(toNumber)) {
            source = lock(fromNumber);
            accessPolicy.requireOwner(source, authentication);
            throw new InvalidTransactionException(
                    "Transaction Failed: Source and destination accounts must be different.");
        }
        // Lock in a fixed global order (ascending account number) to rule out deadlocks.
        boolean fromFirst = fromNumber.compareTo(toNumber) < 0;
        Account first = lock(fromFirst ? fromNumber : toNumber);
        Account second = lock(fromFirst ? toNumber : fromNumber);
        source = fromFirst ? first : second;
        destination = fromFirst ? second : first;

        accessPolicy.requireOwner(source, authentication);
        requireActive(source);
        requireActive(destination);
        BigDecimal amount = requirePositive(rawAmount);
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InsufficientBalanceException("Transaction Failed: Insufficient balance in source account.");
        }

        source.debit(amount);
        destination.credit(amount);

        String ref = newRef();
        String note = describe(description, "Transfer");
        Transaction debitEntry = transactionRepository.save(new Transaction(source, ref,
                TransactionType.TRANSFER, TransactionDirection.DEBIT, amount, TransactionStatus.SUCCESS,
                note, destination.getAccountNumber(), source.getBalance()));
        transactionRepository.save(new Transaction(destination, ref,
                TransactionType.TRANSFER, TransactionDirection.CREDIT, amount, TransactionStatus.SUCCESS,
                note, source.getAccountNumber(), destination.getBalance()));

        return new TransferResponse(ref, source.getAccountNumber(), destination.getAccountNumber(), amount,
                TransactionStatus.SUCCESS, source.getBalance(), debitEntry.getCreatedAt());
    }

    private Account lock(String accountNumber) {
        return accountRepository.findByAccountNumberForUpdate(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
    }

    private void requireActive(Account account) {
        if (!account.isActive()) {
            throw new AccountInactiveException(account.getAccountNumber());
        }
    }

    private BigDecimal requirePositive(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidAmountException("Amount must be greater than zero");
        }
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String newRef() {
        return UUID.randomUUID().toString();
    }

    private String describe(String description, String fallback) {
        return description == null || description.isBlank() ? fallback : description.trim();
    }
}
