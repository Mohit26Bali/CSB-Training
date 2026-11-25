package com.cartnest.dto;

import jakarta.validation.constraints.Size;

/** Both optional: without an address the default address is shown; without a coupon none is applied. */
public record CheckoutPreviewRequest(
        Long addressId,
        @Size(max = 20, message = "couponCode must be at most 20 characters") String couponCode) {
}
