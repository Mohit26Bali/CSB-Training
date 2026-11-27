package com.cartnest.dto;

import jakarta.validation.constraints.NotNull;

public record WishlistAddRequest(@NotNull(message = "productId is required") Long productId) {
}
