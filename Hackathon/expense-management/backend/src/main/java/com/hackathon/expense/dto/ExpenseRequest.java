package com.hackathon.expense.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ExpenseRequest(
        @NotNull(message = "Date is required") LocalDate expenseDate,
        @NotBlank(message = "Category is required") String category,
        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Amount can have at most 2 decimal places") BigDecimal amount,
        @NotBlank(message = "Description is required")
        @Size(max = 500, message = "Description must be at most 500 characters") String description) {
}
