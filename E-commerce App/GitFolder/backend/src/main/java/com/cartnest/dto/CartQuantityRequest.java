package com.cartnest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartQuantityRequest(
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be between 1 and 10")
        @Max(value = 10, message = "quantity must be between 1 and 10") Integer quantity) {
}
