package com.csb.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.csb.banking.entity.TransactionDirection;
import com.csb.banking.entity.TransactionStatus;
import com.csb.banking.entity.TransactionType;
import com.csb.banking.exception.InvalidFilterException;

/**
 * Optional filters for the transaction history (project description, section 12: "filtering by date range, transaction
 * type, amount, and status"). Every field may be null, meaning "no restriction"; all given fields must match (AND).
 *
 * <p>Built by {@code controller/TransactionController} from query parameters such as
 * {@code ?from=2026-10-01&to=2026-10-31&type=TRANSFER&status=SUCCESS&minAmount=100}, validated here, and turned into a
 * database query by {@code repository/TransactionSpecifications}. {@code to} is inclusive: the whole of that day is
 * included. Amount bounds are inclusive too.
 */
public record TransactionFilter(
        LocalDate from,
        LocalDate to,
        TransactionType type,
        TransactionStatus status,
        TransactionDirection direction,
        BigDecimal minAmount,
        BigDecimal maxAmount) {

    public TransactionFilter {
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidFilterException("'from' date must not be after 'to' date");
        }
        if (minAmount != null && minAmount.signum() < 0) {
            throw new InvalidFilterException("'minAmount' must not be negative");
        }
        if (maxAmount != null && maxAmount.signum() < 0) {
            throw new InvalidFilterException("'maxAmount' must not be negative");
        }
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidFilterException("'minAmount' must not be greater than 'maxAmount'");
        }
    }

    /** A filter that restricts nothing. */
    public static TransactionFilter none() {
        return new TransactionFilter(null, null, null, null, null, null, null);
    }
}
