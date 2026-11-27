package com.cartnest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.PaymentMethod;
import com.cartnest.entity.PaymentStatus;

public record OrderDetailResponse(String orderNumber, OrderStatus status, LocalDateTime placedAt, String customerName,
        String customerEmail, List<OrderItemResponse> items, ShippingAddress shippingAddress,
        PaymentMethod paymentMethod, PaymentStatus paymentStatus, String paymentReference, BigDecimal totalMrp,
        BigDecimal productDiscount, BigDecimal itemTotal, String couponCode, BigDecimal couponDiscount,
        BigDecimal deliveryFee, BigDecimal grandTotal, List<OrderEvent> timeline, boolean cancellable,
        String cancelReason) {
}
