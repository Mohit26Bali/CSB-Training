package com.csb.banking.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.csb.banking.entity.Transaction;

/**
 * Storage for the {@link Transaction} ledger.
 *
 * <p>Written by {@code service/AccountService} (opening deposit), {@code service/LedgerService} (successful
 * operations), {@code service/TransactionAuditService} (FAILED rows) and {@code config/DataInitializer}.
 * Read by {@code service/TransactionService#getHistory}, which runs a paged, sorted query built by
 * {@link TransactionSpecifications} (account + optional filters) through {@code JpaSpecificationExecutor#findAll}.
 */
public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
}
