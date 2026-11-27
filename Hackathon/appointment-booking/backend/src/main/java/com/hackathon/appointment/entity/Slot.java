package com.hackathon.appointment.entity;

import java.time.Duration;
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

/** A window of time a provider offers, [startTime, endTime). A closed slot has been withdrawn and cannot be booked. */
@Entity
@Table(name = "slots")
public class Slot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(nullable = false)
    private LocalDateTime startTime;

    @Column(nullable = false)
    private LocalDateTime endTime;

    @Column(nullable = false)
    private boolean closed;

    protected Slot() {
    }

    public Slot(Provider provider, LocalDateTime startTime, LocalDateTime endTime) {
        this.provider = provider;
        this.startTime = startTime;
        this.endTime = endTime;
    }

    public void close() {
        this.closed = true;
    }

    public long minutes() {
        return Duration.between(startTime, endTime).toMinutes();
    }

    public Long getId() {
        return id;
    }

    public Provider getProvider() {
        return provider;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public boolean isClosed() {
        return closed;
    }
}
