package com.csb.banking.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.csb.banking.entity.AccountStatus;
import com.csb.banking.entity.AccountType;

/**
 * Public view of an {@code entity/Account} (account details screen, account lists).
 *
 * <p>Created only by {@code mapper/AccountMapper#toResponse}. Contains the owner's display name but none of the
 * owner's contact details or internal ids.
 */
public record AccountResponse(
        String accountNumber,
        AccountType accountType,
        AccountStatus status,
        BigDecimal balance,
        String customerName,
        LocalDateTime createdAt) {
}
