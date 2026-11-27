package com.hackathon.food.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderLineBody(
        @NotNull(message = "Choose a dish") Long menuItemId,
        @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be at least 1") @Max(value = 20, message = "At most 20 of one dish") Integer quantity) {
}
