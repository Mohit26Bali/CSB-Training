package com.cartnest.dto;

import java.time.LocalDateTime;

import com.cartnest.entity.OrderStatus;

/** One step of the tracking timeline. */
public record OrderEvent(OrderStatus status, String note, LocalDateTime at) {
}
