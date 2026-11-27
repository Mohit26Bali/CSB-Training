package com.cartnest.dto;

import jakarta.validation.constraints.Size;

public record CheckoutPreviewRequest(
        Long addressId,
        @Size(max = 20, message = "couponCode must be at most 20 characters") String couponCode) {
}
