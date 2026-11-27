package com.cartnest.dto;

import com.cartnest.entity.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PlaceOrderRequest(
        @NotNull(message = "addressId is required") Long addressId,
        @NotNull(message = "paymentMethod is required (COD, UPI or CARD)") PaymentMethod paymentMethod,
        @Size(max = 20, message = "couponCode must be at most 20 characters") String couponCode,
        @Pattern(regexp = "[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}", message = "Enter a valid UPI id, e.g. name@okbank")
        String upiId) {
}
