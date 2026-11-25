package com.csb.banking.dto;

import java.math.BigDecimal;

import com.csb.banking.entity.AccountType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/accounts}. The owner is never sent: {@code service/AccountService#createAccount} takes
 * it from the authenticated user, so a customer cannot open an account for somebody else.
 *
 * <p>{@code initialDeposit} is optional; when it is greater than zero the service also records a DEPOSIT row in
 * the transaction ledger.
 */
public record CreateAccountRequest(
        @NotNull(message = "account type is required (SAVINGS or CURRENT)")
        AccountType accountType,

        @DecimalMin(value = "0.00", message = "initial deposit cannot be negative")
        @Digits(integer = 15, fraction = 2, message = "initial deposit must have at most 2 decimal places")
        BigDecimal initialDeposit) {
}
