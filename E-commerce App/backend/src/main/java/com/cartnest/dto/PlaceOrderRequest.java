package com.cartnest.dto;

import com.cartnest.entity.PaymentMethod;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Places an order for everything in the cart. upiId is only used for UPI payments; the payment gateway is simulated
 * and declines any UPI id ending in "@fail", so a failed payment can be demonstrated.
 */
public record PlaceOrderRequest(
        @NotNull(message = "addressId is required") Long addressId,
        @NotNull(message = "paymentMethod is required (COD, UPI or CARD)") PaymentMethod paymentMethod,
        @Size(max = 20, message = "couponCode must be at most 20 characters") String couponCode,
        @Pattern(regexp = "[a-zA-Z0-9.\\-_]{2,64}@[a-zA-Z]{2,32}", message = "Enter a valid UPI id, e.g. name@okbank")
        String upiId) {
}
