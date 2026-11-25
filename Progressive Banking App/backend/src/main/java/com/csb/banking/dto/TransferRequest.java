package com.csb.banking.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/transfers}.
 *
 * <p>Received by {@code controller/TransactionController}, processed by {@code service/TransactionService#transfer}.
 * Only the format is checked here; existence, ownership, status, "different accounts" and balance are business rules
 * enforced by {@code service/LedgerService}.
 */
public record TransferRequest(
        @NotBlank(message = "source account is required") String fromAccount,
        @NotBlank(message = "destination account is required") String toAccount,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "amount must be greater than zero")
        @Digits(integer = 15, fraction = 2, message = "amount must have at most 2 decimal places")
        BigDecimal amount,

        @Size(max = 200, message = "description must be at most 200 characters")
        String description) {
}
