package com.hackathon.hotel.entity;

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

/** A guest's request for a room for [checkIn, checkOut). The check-out day itself is free for the next guest. */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false)
    private LocalDate checkIn;

    @Column(nullable = false)
    private LocalDate checkOut;

    @Column(nullable = false)
    private int guests;

    @Column(nullable = false)
    private int nights;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(length = 300)
    private String specialRequests;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BookingStatus status = BookingStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime confirmedAt;

    private String confirmedBy;

    private LocalDateTime cancelledAt;

    private String cancelledBy;

    @Column(length = 300)
    private String cancelReason;

    protected Booking() {
    }

    public Booking(Room room, User customer, LocalDate checkIn, LocalDate checkOut, int guests, BigDecimal totalPrice, int nights, String specialRequests) {
        this.room = room;
        this.customer = customer;
        this.checkIn = checkIn;
        this.checkOut = checkOut;
        this.guests = guests;
        this.nights = nights;
        this.totalPrice = totalPrice;
        this.specialRequests = specialRequests;
    }

    public void confirm(String admin) {
        this.status = BookingStatus.CONFIRMED;
        this.confirmedAt = LocalDateTime.now();
        this.confirmedBy = admin;
    }

    public void cancel(String by, String reason) {
        this.status = BookingStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = by;
        this.cancelReason = reason;
    }

    /** Used by the demo data. */
    public void forceState(BookingStatus status, LocalDateTime createdAt, String by, String reason) {
        this.status = status;
        this.createdAt = createdAt;
        if (status == BookingStatus.CONFIRMED) {
            this.confirmedAt = createdAt.plusHours(3);
            this.confirmedBy = by;
        } else if (status == BookingStatus.CANCELLED) {
            this.cancelledAt = createdAt.plusHours(5);
            this.cancelledBy = by;
            this.cancelReason = reason;
        }
    }

    public boolean overlaps(LocalDate in, LocalDate out) {
        return checkIn.isBefore(out) && in.isBefore(checkOut);
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public User getCustomer() {
        return customer;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public int getGuests() {
        return guests;
    }

    public int getNights() {
        return nights;
    }

    public BigDecimal getTotalPrice() {
        return totalPrice;
    }

    public String getSpecialRequests() {
        return specialRequests;
    }

    public BookingStatus getStatus() {
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
