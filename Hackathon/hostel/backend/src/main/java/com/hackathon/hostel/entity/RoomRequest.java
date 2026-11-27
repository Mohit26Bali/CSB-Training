package com.hackathon.hostel.entity;

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

/** A student's request for a room. Pending requests, oldest first, are the waiting list. */
@Entity
@Table(name = "room_requests")
public class RoomRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(length = 12)
    private RoomType preferredType;

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

    /** The room given on approval. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    protected RoomRequest() {
    }

    public RoomRequest(User student, RoomType preferredType, String note) {
        this.student = student;
        this.preferredType = preferredType;
        this.note = note;
    }

    public void approve(String admin, Room room) {
        decide(RequestStatus.APPROVED, admin, null);
        this.room = room;
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

    public User getStudent() {
        return student;
    }

    public RoomType getPreferredType() {
        return preferredType;
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

    public Room getRoom() {
        return room;
    }
}
