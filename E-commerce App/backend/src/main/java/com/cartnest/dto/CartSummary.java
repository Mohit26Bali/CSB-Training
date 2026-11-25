package com.cartnest.dto;

import java.math.BigDecimal;

/** totalMrp - discount = subtotal; subtotal + deliveryFee = total. */
public record CartSummary(int itemCount, BigDecimal totalMrp, BigDecimal discount, BigDecimal subtotal, BigDecimal deliveryFee,
        BigDecimal total, BigDecimal freeDeliveryThreshold) {
}
