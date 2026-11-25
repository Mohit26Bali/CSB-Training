package com.csb.banking.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.banking.entity.Account;
import com.csb.banking.entity.Customer;

import jakarta.persistence.LockModeType;

/**
 * Queries on {@link Account}.
 *
 * <p>Used by {@code service/AccountService} (customer side), {@code service/AdminAccountService} (administrator
 * side), {@code service/AccountNumberGenerator} (uniqueness check) and the money services
 * ({@code service/LedgerService}, {@code service/TransactionService}, {@code service/TransactionAuditService}).
 */
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    List<Account> findByCustomerOrderByCreatedAtDesc(Customer customer);

    List<Account> findAllByOrderByCreatedAtDesc();

    /**
     * Loads the account with a database row lock ({@code SELECT ... FOR UPDATE}). Every balance change goes through
     * this query, so two requests on the same account run one after the other and cannot overdraw it.
     * Must be called inside a transaction ({@code service/LedgerService}).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(@Param("accountNumber") String accountNumber);
}
