package com.cartnest.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cartnest.entity.DiscountType;

public record CouponResponse(String code, String description, DiscountType discountType, BigDecimal discountValue,
        BigDecimal maxDiscount, BigDecimal minOrderAmount, LocalDateTime validUntil) {
}
