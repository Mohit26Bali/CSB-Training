package com.hackathon.hostel.entity;

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

    /** Stored upper case, unique ("A-101"). */
    @Column(nullable = false, unique = true, length = 20)
    private String number;

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RoomType type;

    @Column(nullable = false)
    private int capacity;

    /** An inactive room (under maintenance) takes no new students. */
    @Column(nullable = false)
    private boolean active = true;

    protected Room() {
    }

    public Room(String number, int floor, RoomType type, int capacity) {
        update(number, floor, type, capacity, true);
    }

    public final void update(String number, int floor, RoomType type, int capacity, boolean active) {
        this.number = number;
        this.floor = floor;
        this.type = type;
        this.capacity = capacity;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public int getFloor() {
        return floor;
    }

    public RoomType getType() {
        return type;
    }

    public int getCapacity() {
        return capacity;
    }

    public boolean isActive() {
        return active;
    }
}
