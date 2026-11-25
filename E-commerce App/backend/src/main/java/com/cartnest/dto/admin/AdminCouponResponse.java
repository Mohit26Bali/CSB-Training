package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.cartnest.entity.DiscountType;

/** state: ACTIVE, SCHEDULED (not started), EXPIRED, USED_UP or DISABLED. */
public record AdminCouponResponse(Long id, String code, String description, DiscountType discountType, BigDecimal discountValue,
        BigDecimal maxDiscount, BigDecimal minOrderAmount, LocalDateTime validFrom, LocalDateTime validUntil,
        Integer usageLimit, int perUserLimit, int usedCount, boolean active, String state) {
}
