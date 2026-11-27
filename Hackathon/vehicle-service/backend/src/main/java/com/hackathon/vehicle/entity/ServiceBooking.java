package com.hackathon.vehicle.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
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

/** A vehicle's visit to the service center from startDate to endDate (both days inclusive). */
@Entity
@Table(name = "service_bookings")
public class ServiceBooking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private BookingStatus status = BookingStatus.BOOKED;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedCost;

    @Column(precision = 10, scale = 2)
    private BigDecimal finalCost;

    @Column(length = 300)
    private String notes;

    @Column(length = 500)
    private String workNotes;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime startedAt;

    private String startedBy;

    private LocalDateTime completedAt;

    private String completedBy;

    private LocalDateTime cancelledAt;

    private String cancelledBy;

    @Column(length = 300)
    private String cancelReason;

    protected ServiceBooking() {
    }

    public ServiceBooking(Vehicle vehicle, User customer, ServiceType serviceType, LocalDate startDate, BigDecimal estimatedCost, String notes) {
        this.vehicle = vehicle;
        this.customer = customer;
        this.serviceType = serviceType;
        this.startDate = startDate;
        this.endDate = startDate.plusDays(serviceType.getDurationDays() - 1L);
        this.estimatedCost = estimatedCost;
        this.notes = notes;
    }

    public void start(String admin) {
        this.status = BookingStatus.IN_SERVICE;
        this.startedAt = LocalDateTime.now();
        this.startedBy = admin;
    }

    public void complete(String admin, BigDecimal finalCost, String workNotes) {
        this.status = BookingStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.completedBy = admin;
        this.finalCost = finalCost;
        this.workNotes = workNotes;
    }

    public void cancel(String by, String reason) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    /** Used by the demo data. */
    public void forceState(BookingStatus status, LocalDateTime createdAt, String by, BigDecimal finalCost, String workNotes, String reason) {
        this.createdAt = createdAt;
        this.status = status;
        LocalDateTime day = startDate.atTime(9, 30);
        if (status == BookingStatus.IN_SERVICE || status == BookingStatus.COMPLETED) {
            this.startedAt = day;
            this.startedBy = by;
        }
        if (status == BookingStatus.COMPLETED) {
            this.completedAt = endDate.atTime(17, 15);
            this.completedBy = by;
            this.finalCost = finalCost;
            this.workNotes = workNotes;
        }
        if (status == BookingStatus.CANCELLED) {
            this.cancelledAt = createdAt.plusHours(20);
            this.cancelledBy = by;
            this.cancelReason = reason;
        }
    }

    public boolean covers(LocalDate day) {
        return !startDate.isAfter(day) && !endDate.isBefore(day);
    }

    public Long getId() {
        return id;
    }

    public Vehicle getVehicle() {
        return vehicle;
    }

    public User getCustomer() {
        return customer;
    }

    public ServiceType getServiceType() {
        return serviceType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public BigDecimal getEstimatedCost() {
        return estimatedCost;
    }

    public BigDecimal getFinalCost() {
        return finalCost;
    }

    public String getNotes() {
        return notes;
    }

    public String getWorkNotes() {
        return workNotes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public String getStartedBy() {
        return startedBy;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public String getCompletedBy() {
        return completedBy;
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
