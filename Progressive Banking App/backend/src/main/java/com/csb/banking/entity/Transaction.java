package com.csb.banking.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * One line of the audit ledger (table {@code transactions}); every financial operation writes at least one.
 * Rows are append-only: there are no setters, so history cannot be edited after the fact.
 *
 * <p>Relations:
 * <ul>
 *   <li>Many transactions belong to one {@link Account}.</li>
 *   <li>A transfer writes two rows (DEBIT on the source, CREDIT on the destination) that share the same
 *       {@code transactionRef}, hence the reference is not unique on its own.</li>
 *   <li>Stored by {@code repository/TransactionRepository}; written by {@code service/AccountService}
 *       (opening deposit), {@code service/LedgerService} (deposit, withdraw, transfer) and
 *       {@code service/TransactionAuditService} (FAILED attempts).</li>
 *   <li>The index on (account_id, created_at) serves the "latest transactions first" history query.</li>
 * </ul>
 */
@Entity
@Table(name = "transactions", indexes = @Index(name = "idx_txn_account_created", columnList = "account_id, created_at"))
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "transaction_ref", nullable = false, length = 36)
    private String transactionRef;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TransactionDirection direction;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(length = 255)
    private String description;

    /** The other account of a transfer; null for deposits and withdrawals. */
    @Column(name = "counterparty_account_number", length = 20)
    private String counterpartyAccountNumber;

    /** Balance of {@link #account} right after this entry (equals the unchanged balance for FAILED rows). */
    @Column(name = "balance_after", nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Transaction() {
        // required by JPA
    }

    public Transaction(Account account, String transactionRef, TransactionType type,
                       TransactionDirection direction, BigDecimal amount, TransactionStatus status,
                       String description, String counterpartyAccountNumber, BigDecimal balanceAfter) {
        this.account = account;
        this.transactionRef = transactionRef;
        this.type = type;
        this.direction = direction;
        this.amount = amount;
        this.status = status;
        this.description = description;
        this.counterpartyAccountNumber = counterpartyAccountNumber;
        this.balanceAfter = balanceAfter;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTransactionRef() {
        return transactionRef;
    }

    public Account getAccount() {
        return account;
    }

    public TransactionType getType() {
        return type;
    }

    public TransactionDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public String getCounterpartyAccountNumber() {
        return counterpartyAccountNumber;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
