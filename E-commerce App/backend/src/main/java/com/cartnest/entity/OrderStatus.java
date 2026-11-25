package com.cartnest.entity;

/** PLACED -> CONFIRMED -> SHIPPED -> DELIVERED; CANCELLED is possible until the order ships. */
public enum OrderStatus {
    PLACED,
    CONFIRMED,
    SHIPPED,
    DELIVERED,
    CANCELLED
}
