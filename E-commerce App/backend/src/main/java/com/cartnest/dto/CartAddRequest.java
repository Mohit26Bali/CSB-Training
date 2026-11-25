package com.cartnest.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Adds a variant to the cart; quantity defaults to 1 and is added to what is already in the cart. */
public record CartAddRequest(
        @NotNull(message = "variantId is required") Long variantId,
        @Min(value = 1, message = "quantity must be between 1 and 10")
        @Max(value = 10, message = "quantity must be between 1 and 10") Integer quantity) {
}
