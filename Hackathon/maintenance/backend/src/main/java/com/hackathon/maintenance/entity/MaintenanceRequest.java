package com.hackathon.maintenance.entity;

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

@Entity
@Table(name = "maintenance_requests")
public class MaintenanceRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private Category category;

    @Column(nullable = false, length = 1000)
    private String description;

    @Column(nullable = false, length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private RequestStatus status = RequestStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "resident_id", nullable = false)
    private User resident;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "technician_id")
    private Technician technician;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime assignedAt;

    private LocalDateTime startedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;

    private LocalDateTime updatedAt;

    /** What was done; mandatory when the request is resolved, and visible to the resident. */
    @Column(length = 1000)
    private String resolutionNotes;

    protected MaintenanceRequest() {
    }

    public MaintenanceRequest(Category category, String description, String location, Priority priority, User resident) {
        this.category = category;
        this.description = description;
        this.location = location;
        this.priority = priority;
        this.resident = resident;
    }

    /** Assigning moves an open request to ASSIGNED; reassigning an assigned or in-progress one keeps its status. */
    public void assignTo(Technician technician) {
        this.technician = technician;
        if (status == RequestStatus.OPEN) {
            this.status = RequestStatus.ASSIGNED;
        }
        this.assignedAt = LocalDateTime.now();
        touch();
    }

    public void moveTo(RequestStatus next, String notes) {
        this.status = next;
        LocalDateTime now = LocalDateTime.now();
        switch (next) {
            case IN_PROGRESS -> {
                this.startedAt = this.startedAt == null ? now : this.startedAt;
                this.resolvedAt = null;
            }
            case RESOLVED -> {
                this.resolvedAt = now;
                this.resolutionNotes = notes;
            }
            case CLOSED -> this.closedAt = now;
            default -> {
            }
        }
        touch();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** Used by the demo data. */
    public void forceState(RequestStatus status, Technician technician, LocalDateTime createdAt, LocalDateTime assignedAt,
                           LocalDateTime startedAt, LocalDateTime resolvedAt, LocalDateTime closedAt, String notes) {
        this.status = status;
        this.technician = technician;
        this.createdAt = createdAt;
        this.assignedAt = assignedAt;
        this.startedAt = startedAt;
        this.resolvedAt = resolvedAt;
        this.closedAt = closedAt;
        this.resolutionNotes = notes;
        LocalDateTime last = closedAt != null ? closedAt : resolvedAt != null ? resolvedAt : startedAt != null ? startedAt
                : assignedAt != null ? assignedAt : createdAt;
        this.updatedAt = last;
    }

    public Long getId() {
        return id;
    }

    public Category getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public Priority getPriority() {
        return priority;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public User getResident() {
        return resident;
    }

    public Technician getTechnician() {
        return technician;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }
}
