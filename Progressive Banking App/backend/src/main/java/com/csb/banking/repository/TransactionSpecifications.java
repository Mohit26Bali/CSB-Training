package com.csb.banking.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.csb.banking.dto.TransactionFilter;
import com.csb.banking.entity.Account;
import com.csb.banking.entity.Transaction;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds the dynamic WHERE clause of the history query from a {@link TransactionFilter}.
 *
 * <p>Used by {@code service/TransactionService#getHistory} together with {@link TransactionRepository}, which can run
 * any {@link Specification} because it extends {@code JpaSpecificationExecutor}. Only the filters that are actually
 * set add a condition, so the common "no filter" case is the plain indexed lookup on (account, created_at).
 */
public final class TransactionSpecifications {

    private TransactionSpecifications() {
    }

    public static Specification<Transaction> forAccount(Account account, TransactionFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("account"), account));

            if (filter.from() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("createdAt"), filter.from().atStartOfDay()));
            }
            if (filter.to() != null) {
                // "to" is inclusive, so everything strictly before the start of the NEXT day.
                conditions.add(cb.lessThan(root.get("createdAt"), filter.to().plusDays(1).atStartOfDay()));
            }
            if (filter.type() != null) {
                conditions.add(cb.equal(root.get("type"), filter.type()));
            }
            if (filter.status() != null) {
                conditions.add(cb.equal(root.get("status"), filter.status()));
            }
            if (filter.direction() != null) {
                conditions.add(cb.equal(root.get("direction"), filter.direction()));
            }
            if (filter.minAmount() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("amount"), filter.minAmount()));
            }
            if (filter.maxAmount() != null) {
                conditions.add(cb.lessThanOrEqualTo(root.get("amount"), filter.maxAmount()));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
