package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.PaymentMethod;
import com.cartnest.entity.PaymentStatus;

public record AdminOrderRow(String orderNumber, LocalDateTime placedAt, OrderStatus status, PaymentMethod paymentMethod,
        PaymentStatus paymentStatus, BigDecimal grandTotal, int itemCount, String customerName, String customerEmail,
        String city) {
}
