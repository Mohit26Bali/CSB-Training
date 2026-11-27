package com.hackathon.procurement.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RequestSubmission(
        @NotBlank(message = "Item is required") @Size(max = 120, message = "Item must be at most 120 characters") String itemName,
        @NotBlank(message = "Category is required") String category,
        @NotNull(message = "Quantity is required")
        @Min(value = 1, message = "Quantity must be at least 1")
        @Max(value = 10000, message = "Quantity must be at most 10000") Integer quantity,
        @NotNull(message = "Estimated cost is required")
        @DecimalMin(value = "0.01", message = "Estimated cost must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Estimated cost can have at most 2 decimal places") BigDecimal estimatedUnitCost,
        @NotBlank(message = "Justification is required")
        @Size(max = 500, message = "Justification must be at most 500 characters") String justification) {
}
