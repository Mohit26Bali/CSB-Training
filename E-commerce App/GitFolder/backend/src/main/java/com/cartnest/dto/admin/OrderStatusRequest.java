package com.cartnest.dto.admin;

import com.cartnest.entity.OrderStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrderStatusRequest(@NotNull(message = "status is required (CONFIRMED, SHIPPED or DELIVERED)") OrderStatus status,
        @Size(max = 200, message = "note must be at most 200 characters") String note) {
}
