package com.hackathon.complaints.entity;

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

/** One line of a complaint's timeline: filed, assigned, status changed, feedback given. */
@Entity
@Table(name = "complaint_events")
public class ComplaintEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "complaint_id", nullable = false)
    private Complaint complaint;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EventType type;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private ComplaintStatus status;

    @Column(length = 1000)
    private String note;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    protected ComplaintEvent() {
    }

    public ComplaintEvent(Complaint complaint, User actor, EventType type, ComplaintStatus status, String note) {
        this.complaint = complaint;
        this.actor = actor;
        this.type = type;
        this.status = status;
        this.note = note;
    }

    public Long getId() {
        return id;
    }

    public Complaint getComplaint() {
        return complaint;
    }

    public User getActor() {
        return actor;
    }

    public EventType getType() {
        return type;
    }

    public ComplaintStatus getStatus() {
        return status;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
