package com.hackathon.hotel.entity;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored upper case and unique, e.g. "305". */
    @Column(nullable = false, unique = true, length = 10)
    private String number;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RoomType type;

    @Column(nullable = false)
    private int floor;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerNight;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 500)
    private String description;

    /** Comma separated, e.g. "Wi-Fi, Air conditioning, Sea view". */
    @Column(length = 300)
    private String amenities;

    /** A room that is not active (under renovation) is not offered to guests. */
    @Column(nullable = false)
    private boolean active = true;

    protected Room() {
    }

    public Room(String number, RoomType type, int floor, BigDecimal pricePerNight, int capacity, String description, String amenities) {
        update(number, type, floor, pricePerNight, capacity, description, amenities, true);
    }

    public final void update(String number, RoomType type, int floor, BigDecimal pricePerNight, int capacity, String description,
                             String amenities, boolean active) {
        this.number = number;
        this.type = type;
        this.floor = floor;
        this.pricePerNight = pricePerNight;
        this.capacity = capacity;
        this.description = description;
        this.amenities = amenities;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public RoomType getType() {
        return type;
    }

    public int getFloor() {
        return floor;
    }

    public BigDecimal getPricePerNight() {
        return pricePerNight;
    }

    public int getCapacity() {
        return capacity;
    }

    public String getDescription() {
        return description;
    }

    public String getAmenities() {
        return amenities;
    }

    public boolean isActive() {
        return active;
    }
}
