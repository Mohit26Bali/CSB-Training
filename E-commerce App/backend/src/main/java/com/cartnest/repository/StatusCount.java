package com.cartnest.repository;

import com.cartnest.entity.OrderStatus;

public record StatusCount(OrderStatus status, Long total) {
}
