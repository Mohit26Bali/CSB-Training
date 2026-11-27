package com.hackathon.hostel.entity;

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

/** A student living in a room. vacatedAt is null while the allocation is active. */
@Entity
@Table(name = "room_allocations")
public class RoomAllocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(nullable = false)
    private LocalDateTime allocatedAt = LocalDateTime.now();

    @Column(nullable = false, length = 50)
    private String allocatedBy;

    private LocalDateTime vacatedAt;

    protected RoomAllocation() {
    }

    public RoomAllocation(Room room, User student, String allocatedBy) {
        this.room = room;
        this.student = student;
        this.allocatedBy = allocatedBy;
    }

    public boolean isActive() {
        return vacatedAt == null;
    }

    public void vacate() {
        this.vacatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Room getRoom() {
        return room;
    }

    public User getStudent() {
        return student;
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

    public LocalDateTime getVacatedAt() {
        return vacatedAt;
    }

    public void setVacatedAt(LocalDateTime vacatedAt) {
        this.vacatedAt = vacatedAt;
    }
}
