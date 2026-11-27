package com.hackathon.food.entity;

public enum OrderStatus {
    PLACED,
    ACCEPTED,
    PREPARING,
    READY,
    DELIVERED,
    CANCELLED;

    /** The only status an order may move to next, or null when it cannot move on. */
    public OrderStatus next() {
        return switch (this) {
            case PLACED -> ACCEPTED;
            case ACCEPTED -> PREPARING;
            case PREPARING -> READY;
            case READY -> DELIVERED;
            default -> null;
        };
    }

    public boolean isOpen() {
        return this != DELIVERED && this != CANCELLED;
    }
}
