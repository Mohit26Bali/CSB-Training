package com.csb.banking.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.function.Supplier;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.banking.dto.AmountRequest;
import com.csb.banking.dto.PageResponse;
import com.csb.banking.dto.TransactionFilter;
import com.csb.banking.dto.TransactionResponse;
import com.csb.banking.dto.TransferRequest;
import com.csb.banking.dto.TransferResponse;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.AccountNotFoundException;
import com.csb.banking.exception.BankingException;
import com.csb.banking.mapper.TransactionMapper;
import com.csb.banking.repository.AccountRepository;
import com.csb.banking.repository.TransactionRepository;
import com.csb.banking.repository.TransactionSpecifications;

/**
 * Entry point of the money features for the controller: deposit, withdraw, transfer and transaction history.
 *
 * <p>Division of work (this class is intentionally NOT {@code @Transactional} for the money operations):
 * <pre>
 * TransactionController -> TransactionService --(1) IdempotencyService  optional: runs (2) at most once per key
 *                                              \--(2) LedgerService       one DB transaction, does the work
 *                                              \--(3) TransactionAuditService  records a FAILED row if (2) threw
 * </pre>
 * <ul>
 *   <li>{@code @PreAuthorize} limits moving money to CUSTOMERs; ownership is checked inside the ledger via
 *       {@link AccountAccessPolicy}.</li>
 *   <li>If the ledger rejects an operation with a business exception, a FAILED row is written for the caller's source
 *       account and the exception is rethrown (it becomes a 4xx through {@code exception/GlobalExceptionHandler}).
 *       Rejections flagged non-auditable ({@code BankingException#isAuditable}) are never recorded: an unknown or
 *       foreign account, or a bad idempotency key, is not a transaction attempt.</li>
 *   <li>The optional {@code Idempotency-Key} makes a repeated request (double click, network retry) return the
 *       first response instead of moving money again; see {@link IdempotencyService}.</li>
 *   <li>History is read-only and uses its own read transaction.</li>
 * </ul>
 */
@Service
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final LedgerService ledgerService;
    private final TransactionAuditService auditService;
    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final AccountAccessPolicy accessPolicy;
    private final TransactionMapper transactionMapper;
    private final IdempotencyService idempotencyService;

    public TransactionService(LedgerService ledgerService, TransactionAuditService auditService,
                              AccountRepository accountRepository, TransactionRepository transactionRepository,
                              AccountAccessPolicy accessPolicy, TransactionMapper transactionMapper,
                              IdempotencyService idempotencyService) {
        this.ledgerService = ledgerService;
        this.auditService = auditService;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accessPolicy = accessPolicy;
        this.transactionMapper = transactionMapper;
        this.idempotencyService = idempotencyService;
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public TransactionResponse deposit(String accountNumber, AmountRequest request, String idempotencyKey,
                                       Authentication authentication) {
        return auditingFailures(accountNumber, null, TransactionType.DEPOSIT, TransactionDirection.CREDIT,
                request.amount(), () -> idempotencyService.execute(idempotencyKey, authentication.getName(),
                        fingerprint("DEPOSIT", accountNumber, request.amount(), request.description()),
                        TransactionResponse.class, () -> ledgerService.deposit(
                                accountNumber, request.amount(), request.description(), authentication)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public TransactionResponse withdraw(String accountNumber, AmountRequest request, String idempotencyKey,
                                        Authentication authentication) {
        return auditingFailures(accountNumber, null, TransactionType.WITHDRAWAL, TransactionDirection.DEBIT,
                request.amount(), () -> idempotencyService.execute(idempotencyKey, authentication.getName(),
                        fingerprint("WITHDRAW", accountNumber, request.amount(), request.description()),
                        TransactionResponse.class, () -> ledgerService.withdraw(
                                accountNumber, request.amount(), request.description(), authentication)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    public TransferResponse transfer(TransferRequest request, String idempotencyKey,
                                     Authentication authentication) {
        return auditingFailures(request.fromAccount(), request.toAccount(), TransactionType.TRANSFER,
                TransactionDirection.DEBIT, request.amount(), () -> idempotencyService.execute(
                        idempotencyKey, authentication.getName(),
                        fingerprint("TRANSFER", request.fromAccount() + ">" + request.toAccount(),
                                request.amount(), request.description()),
                        TransferResponse.class, () -> ledgerService.transfer(
                                request.fromAccount(), request.toAccount(), request.amount(),
                                request.description(), authentication)));
    }

    /**
     * Transaction history of one account, newest first, one page at a time (page is zero-based), optionally narrowed
     * by a {@link TransactionFilter} (date range, type, status, direction, amount range).
     */
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getHistory(String accountNumber, int page, int size,
                                                        TransactionFilter filter, Authentication authentication) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new AccountNotFoundException(accountNumber));
        accessPolicy.requireReadAccess(account, authentication);

        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        // id as tie-breaker keeps the order stable for entries created in the same instant.
        PageRequest pageable = PageRequest.of(safePage, safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.DESC, "id")));
        return PageResponse.from(transactionRepository
                .findAll(TransactionSpecifications.forAccount(account, filter), pageable)
                .map(transactionMapper::toResponse));
    }

    /** Canonical text of a request, so a reused idempotency key with different content can be detected. */
    private String fingerprint(String operation, String accounts, BigDecimal amount, String description) {
        return operation + "|" + accounts + "|" + amount.stripTrailingZeros().toPlainString() + "|"
                + (description == null ? "" : description.trim());
    }

    private <T> T auditingFailures(String accountNumber, String counterparty, TransactionType type,
                                   TransactionDirection direction, BigDecimal amount, Supplier<T> operation) {
        try {
            return operation.get();
        } catch (BankingException ex) {
            if (!ex.isAuditable()) {
                throw ex;
            }
            auditService.recordFailure(accountNumber, counterparty, type, direction,
                    amount.setScale(2, RoundingMode.HALF_UP), ex.getMessage());
            throw ex;
        }
    }
}
