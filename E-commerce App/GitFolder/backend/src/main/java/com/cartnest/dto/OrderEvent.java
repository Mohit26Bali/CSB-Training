package com.cartnest.dto;

import java.time.LocalDateTime;

import com.cartnest.entity.OrderStatus;

public record OrderEvent(OrderStatus status, String note, LocalDateTime at) {
}
