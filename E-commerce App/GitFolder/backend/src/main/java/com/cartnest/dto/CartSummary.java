package com.cartnest.dto;

import java.math.BigDecimal;

public record CartSummary(int itemCount, BigDecimal totalMrp, BigDecimal discount, BigDecimal subtotal, BigDecimal deliveryFee,
        BigDecimal total, BigDecimal freeDeliveryThreshold) {
}
