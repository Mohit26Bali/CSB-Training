package com.hackathon.vehicle.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Something the service center offers, for example "Full service". basePrice is for a car; other vehicles use their factor. */
@Entity
@Table(name = "service_types")
public class ServiceType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String name;

    @Column(length = 300)
    private String description;

    @Column(nullable = false)
    private int durationDays;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal basePrice;

    @Column(nullable = false)
    private boolean active = true;

    protected ServiceType() {
    }

    public ServiceType(String name, String description, int durationDays, BigDecimal basePrice, boolean active) {
        update(name, description, durationDays, basePrice, active);
    }

    public void update(String name, String description, int durationDays, BigDecimal basePrice, boolean active) {
        this.name = name;
        this.description = description;
        this.durationDays = durationDays;
        this.basePrice = basePrice;
        this.active = active;
    }

    /** The estimated cost for a vehicle type. */
    public BigDecimal estimateFor(VehicleType type) {
        return basePrice.multiply(type.factor()).setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public int getDurationDays() {
        return durationDays;
    }

    public BigDecimal getBasePrice() {
        return basePrice;
    }

    public boolean isActive() {
        return active;
    }
}
