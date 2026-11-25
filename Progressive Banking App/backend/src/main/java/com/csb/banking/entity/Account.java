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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * A bank account (table {@code accounts}) and its current balance.
 *
 * <p>Relations:
 * <ul>
 *   <li>Many accounts belong to one {@link Customer}.</li>
 *   <li>One account has many {@link Transaction} rows (the foreign key is on the transaction side).</li>
 *   <li>Created by {@code service/AccountService}; its number comes from
 *       {@code service/AccountNumberGenerator}; shown to clients through {@code mapper/AccountMapper}.</li>
 * </ul>
 *
 * <p>Money rules built into the mapping: the balance is a {@link BigDecimal} (never double) with two decimals,
 * and {@code @Version} turns two simultaneous updates of the same row into an optimistic-lock failure instead
 * of a silently lost update. The "balance never negative" rule is enforced by the services that change it.
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Version
    private Long version;

    protected Account() {
        // required by JPA
    }

    /** A new account is always ACTIVE; {@code openingBalance} must already have two decimals. */
    public Account(String accountNumber, AccountType accountType, Customer customer, BigDecimal openingBalance) {
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.customer = customer;
        this.balance = openingBalance;
        this.status = AccountStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /**
     * Adds money. Callers ({@code service/LedgerService}) have already validated the amount; the guard here only
     * protects the invariant against programming errors.
     */
    public void credit(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Credit amount must be positive");
        }
        this.balance = this.balance.add(amount);
    }

    /** Removes money; the balance may never become negative (core business rule), whatever the caller does. */
    public void debit(BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Debit amount must be positive");
        }
        BigDecimal result = this.balance.subtract(amount);
        if (result.signum() < 0) {
            throw new IllegalStateException("Debit would make the balance negative");
        }
        this.balance = result;
    }

    public Long getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public Customer getCustomer() {
        return customer;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
