package com.cartnest.entity;

/** COD stays PENDING until delivery; a cancelled paid order is REFUNDED. */
public enum PaymentStatus {
    PENDING,
    PAID,
    REFUNDED,
    CANCELLED
}
