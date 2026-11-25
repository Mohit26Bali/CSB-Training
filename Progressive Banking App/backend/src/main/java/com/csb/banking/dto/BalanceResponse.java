package com.csb.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Answer of {@code GET /api/accounts/{accountNumber}/balance}, built by {@code service/AccountService#getBalance}.
 * {@code asOf} tells the client when the balance was read, since it changes with every transaction.
 */
public record BalanceResponse(String accountNumber, BigDecimal balance, LocalDateTime asOf) {
}
