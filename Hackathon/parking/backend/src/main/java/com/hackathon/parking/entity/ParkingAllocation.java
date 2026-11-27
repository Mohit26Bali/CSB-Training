package com.hackathon.parking.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** An employee holding a slot. releasedAt is null while the allocation is active. */
@Entity
@Table(name = "parking_allocations")
public class ParkingAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private ParkingSlot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(nullable = false, length = 15)
    private String vehicleNumber;

    @Column(nullable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Column(nullable = false, length = 50)
    private String allocatedBy;

    private LocalDateTime releasedAt;

    private String releasedBy;

    protected ParkingAllocation() {
    }

    public ParkingAllocation(ParkingSlot slot, User employee, String vehicleNumber, String allocatedBy) {
        this.slot = slot;
        this.employee = employee;
        this.vehicleNumber = vehicleNumber;
        this.allocatedBy = allocatedBy;
    }

    public boolean isActive() {
        return releasedAt == null;
    }

    public void release(String admin) {
        this.releasedAt = LocalDateTime.now();
        this.releasedBy = admin;
    }

    public Long getId() {
        return id;
    }

    public ParkingSlot getSlot() {
        return slot;
    }

    public User getEmployee() {
        return employee;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public LocalDateTime getAllocatedAt() {
        return allocatedAt;
    }

    public void setAllocatedAt(LocalDateTime allocatedAt) {
        this.allocatedAt = allocatedAt;
    }

    public String getAllocatedBy() {
        return allocatedBy;
    }

    public LocalDateTime getReleasedAt() {
        return releasedAt;
    }

    public void setReleasedAt(LocalDateTime releasedAt) {
        this.releasedAt = releasedAt;
    }

    public String getReleasedBy() {
        return releasedBy;
    }
}
