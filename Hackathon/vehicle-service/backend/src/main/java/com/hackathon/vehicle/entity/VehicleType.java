package com.hackathon.vehicle.entity;

/** The kind of vehicle; it sets the price factor of every service. */
public enum VehicleType {
    BIKE("0.60"),
    CAR("1.00"),
    SUV("1.30"),
    VAN("1.50");

    private final String factor;

    VehicleType(String factor) {
        this.factor = factor;
    }

    public java.math.BigDecimal factor() {
        return new java.math.BigDecimal(factor);
    }
}
