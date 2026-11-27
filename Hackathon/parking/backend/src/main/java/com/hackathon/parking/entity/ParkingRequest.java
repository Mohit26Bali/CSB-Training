package com.hackathon.parking.entity;

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

/** An employee's request for a slot. The admin picks the actual slot on approval. */
@Entity
@Table(name = "parking_requests")
public class ParkingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(nullable = false, length = 15)
    private String vehicleNumber;

    private Integer preferredFloor;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 300)
    private String decisionReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id")
    private ParkingSlot slot;

    protected ParkingRequest() {
    }

    public ParkingRequest(User employee, String vehicleNumber, Integer preferredFloor, String note) {
        this.employee = employee;
        this.vehicleNumber = vehicleNumber;
        this.preferredFloor = preferredFloor;
        this.note = note;
    }

    public void approve(String admin, ParkingSlot slot) {
        decide(RequestStatus.APPROVED, admin, null);
        this.slot = slot;
    }

    public void reject(String admin, String reason) {
        decide(RequestStatus.REJECTED, admin, reason);
    }

    public void cancel() {
        this.status = RequestStatus.CANCELLED;
        this.decidedAt = LocalDateTime.now();
    }

    private void decide(RequestStatus status, String admin, String reason) {
        this.status = status;
        this.decidedBy = admin;
        this.decidedAt = LocalDateTime.now();
        this.decisionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public User getEmployee() {
        return employee;
    }

    public String getVehicleNumber() {
        return vehicleNumber;
    }

    public Integer getPreferredFloor() {
        return preferredFloor;
    }

    public String getNote() {
        return note;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }

    public void setDecidedAt(LocalDateTime decidedAt) {
        this.decidedAt = decidedAt;
    }

    public String getDecisionReason() {
        return decisionReason;
    }

    public ParkingSlot getSlot() {
        return slot;
    }
}
