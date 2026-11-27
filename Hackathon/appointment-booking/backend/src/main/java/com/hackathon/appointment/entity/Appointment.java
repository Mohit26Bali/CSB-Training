package com.hackathon.appointment.entity;

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

/** A customer's booking of one slot. At most one PENDING or CONFIRMED appointment exists per slot at any time. */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private Slot slot;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(length = 300)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime confirmedAt;

    private String confirmedBy;

    private LocalDateTime cancelledAt;

    private String cancelledBy;

    @Column(length = 300)
    private String cancelReason;

    protected Appointment() {
    }

    public Appointment(Slot slot, User customer, String reason) {
        this.slot = slot;
        this.customer = customer;
        this.reason = reason;
    }

    public void confirm(String provider) {
        this.status = AppointmentStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.confirmedBy = provider;
    }

    public void cancel(String by, String reason) {
        this.status = AppointmentStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    /** Used by the demo data. */
    public void forceState(AppointmentStatus status, LocalDateTime createdAt, String by, String reason) {
        this.createdAt = createdAt;
        this.status = status;
        if (status == AppointmentStatus.CONFIRMED) {
            this.confirmedAt = createdAt.plusHours(4);
            this.confirmedBy = by;
        } else if (status == AppointmentStatus.CANCELLED) {
            this.cancelledAt = createdAt.plusHours(9);
            this.cancelledBy = by;
            this.cancelReason = reason;
        }
    }

    public Long getId() {
        return id;
    }

    public Slot getSlot() {
        return slot;
    }

    public User getCustomer() {
        return customer;
    }

    public String getReason() {
        return reason;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public String getCancelReason() {
        return cancelReason;
    }
}
