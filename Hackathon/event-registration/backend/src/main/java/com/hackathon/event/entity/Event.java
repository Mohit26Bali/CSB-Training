package com.hackathon.event.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 40)
    private String category;

    @Column(nullable = false, length = 120)
    private String venue;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private int capacity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private EventStatus status = EventStatus.DRAFT;

    @Column(nullable = false, length = 50)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected Event() {
    }

    public Event(String title, String category, String venue, String description, LocalDateTime startsAt,
                 LocalDateTime endsAt, int capacity, String createdBy) {
        update(title, category, venue, description, startsAt, endsAt, capacity);
        this.createdBy = createdBy;
    }

    public final void update(String title, String category, String venue, String description, LocalDateTime startsAt,
                             LocalDateTime endsAt, int capacity) {
        this.title = title;
        this.category = category;
        this.venue = venue;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.capacity = capacity;
    }

    public void publish() {
        this.status = EventStatus.PUBLISHED;
    }

    public void cancel() {
        this.status = EventStatus.CANCELLED;
    }

    public boolean hasStarted(LocalDateTime now) {
        return !startsAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getCategory() {
        return category;
    }

    public String getVenue() {
        return venue;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public int getCapacity() {
        return capacity;
    }

    public EventStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
