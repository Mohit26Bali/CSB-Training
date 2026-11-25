package com.cartnest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.PaymentMethod;

/** A row of the "My orders" list. */
public record OrderSummaryResponse(String orderNumber, OrderStatus status, LocalDateTime placedAt, BigDecimal grandTotal, int itemCount,
        List<String> thumbnails, PaymentMethod paymentMethod) {
}
