package com.cartnest.dto.admin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record AdminCustomerRow(Long id, String fullName, String email, String phone, boolean enabled, LocalDateTime joinedAt,
        long orderCount, BigDecimal totalSpent) {
}
