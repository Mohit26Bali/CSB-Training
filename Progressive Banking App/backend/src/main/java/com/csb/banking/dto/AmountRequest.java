package com.csb.banking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/accounts/{accountNumber}/deposit} and {@code .../withdraw}.
 *
 * <p>Received by {@code controller/TransactionController}, processed by {@code service/TransactionService}. The
 * rule "amount must be greater than zero" (project description, section 13) is checked here first
 * ({@code @DecimalMin} with {@code inclusive=false}) and once more in {@code service/LedgerService}.
 */
public record AmountRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 200, message = "description must be at most 200 characters")
        String description) {
}
