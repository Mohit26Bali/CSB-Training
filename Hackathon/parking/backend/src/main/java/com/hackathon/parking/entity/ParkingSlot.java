package com.hackathon.parking.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parking_slots")
public class ParkingSlot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stored upper case and unique, e.g. "L2-07". */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false)
    private int floor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private SlotStatus status = SlotStatus.AVAILABLE;

    protected ParkingSlot() {
    }

    public ParkingSlot(String code, int floor, SlotStatus status) {
        update(code, floor, status);
    }

    public final void update(String code, int floor, SlotStatus status) {
        this.code = code;
        this.floor = floor;
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public int getFloor() {
        return floor;
    }

    public SlotStatus getStatus() {
        return status;
    }
}
