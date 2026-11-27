package com.hackathon.helpdesk.entity;

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
@Table(name = "tickets")
public class Ticket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Category category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TicketStatus status = TicketStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_id", nullable = false)
    private User requester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    /** When the SLA runs out: created time plus the priority's hours. */
    @Column(nullable = false)
    private LocalDateTime dueAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;

    @Column(length = 1000)
    private String resolution;

    protected Ticket() {
    }

    public Ticket(String title, String description, Category category, Priority priority, User requester) {
        this.title = title;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.requester = requester;
        this.dueAt = createdAt.plusHours(priority.getSlaHours());
    }

    /** Used by the demo data to create tickets that started in the past. */
    public void startedAt(LocalDateTime when) {
        this.createdAt = when;
        this.dueAt = when.plusHours(priority.getSlaHours());
    }

    public void assignTo(User agent) {
        this.assignee = agent;
        touch();
    }

    public void moveTo(TicketStatus next, String note) {
        this.status = next;
        LocalDateTime now = LocalDateTime.now();
        if (next == TicketStatus.RESOLVED) {
            this.resolvedAt = now;
            this.resolution = note;
        } else if (next == TicketStatus.CLOSED) {
            this.closedAt = now;
        } else if (next == TicketStatus.IN_PROGRESS) {
            this.resolvedAt = null; // reopened after a fix that did not hold
        }
        touch();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public Category getCategory() {
        return category;
    }

    public Priority getPriority() {
        return priority;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public User getRequester() {
        return requester;
    }

    public User getAssignee() {
        return assignee;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public LocalDateTime getDueAt() {
        return dueAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getResolution() {
        return resolution;
    }

    public void setResolution(String resolution) {
        this.resolution = resolution;
    }

    public void forceStatus(TicketStatus status) {
        this.status = status;
    }
}
