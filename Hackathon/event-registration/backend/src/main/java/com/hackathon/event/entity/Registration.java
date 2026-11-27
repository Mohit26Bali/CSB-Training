package com.hackathon.event.entity;

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
import jakarta.persistence.UniqueConstraint;

/**
 * One employee's place (or waitlist spot) at one event. The unique constraint means a person has at most one row per
 * event: cancelling keeps the row and registering again re-activates it.
 */
@Entity
@Table(name = "registrations", uniqueConstraints = @UniqueConstraint(columnNames = { "event_id", "user_id" }))
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RegistrationStatus status;

    /** When the person joined (or re-joined); the waitlist is served in this order. */
    @Column(nullable = false)
    private LocalDateTime registeredAt = LocalDateTime.now();

    protected Registration() {
    }

    public Registration(Event event, User user, RegistrationStatus status) {
        this.event = event;
        this.user = user;
        this.status = status;
    }

    public boolean isActive() {
        return RegistrationStatus.ACTIVE.contains(status);
    }

    public void reactivate(RegistrationStatus status) {
        this.status = status;
        this.registeredAt = LocalDateTime.now();
    }

    public void setStatus(RegistrationStatus status) {
        this.status = status;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
    }

    public User getUser() {
        return user;
    }

    public RegistrationStatus getStatus() {
        return status;
    }

    public LocalDateTime getRegisteredAt() {
        return registeredAt;
    }

    public void setRegisteredAt(LocalDateTime registeredAt) {
        this.registeredAt = registeredAt;
    }
}
