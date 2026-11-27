package com.hackathon.vehicle.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A customer's vehicle. The registration number is stored upper-case without spaces and is unique. */
@Entity
@Table(name = "vehicles")
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(nullable = false, unique = true, length = 15)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private VehicleType type;

    @Column(nullable = false, length = 40)
    private String make;

    @Column(nullable = false, length = 40)
    private String model;

    @Column(name = "model_year", nullable = false)
    private int year;

    @Column(nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();

    protected Vehicle() {
    }

    public Vehicle(User owner, String number, VehicleType type, String make, String model, int year) {
        this.owner = owner;
        this.number = number;
        this.type = type;
        this.make = make;
        this.model = model;
        this.year = year;
    }

    public Long getId() {
        return id;
    }

    public User getOwner() {
        return owner;
    }

    public String getNumber() {
        return number;
    }

    public VehicleType getType() {
        return type;
    }

    public String getMake() {
        return make;
    }

    public String getModel() {
        return model;
    }

    public int getYear() {
        return year;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }
}
