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

@Entity
@Table(name = "complaints")
public class Complaint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String subject;

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
    private ComplaintStatus status = ComplaintStatus.OPEN;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt;

    private LocalDateTime resolvedAt;

    private LocalDateTime closedAt;

    /** What the agent did about it; mandatory when the complaint is resolved, and visible to the customer. */
    @Column(length = 1000)
    private String resolution;

    /** The customer's 1 to 5 rating of how it was handled, given once after it is resolved. */
    private Integer rating;

    @Column(length = 500)
    private String feedback;

    protected Complaint() {
    }

    public Complaint(String subject, String description, Category category, Priority priority, User customer) {
        this.subject = subject;
        this.description = description;
        this.category = category;
        this.priority = priority;
        this.customer = customer;
    }

    public void assignTo(User agent) {
        this.assignee = agent;
        touch();
    }

    public void moveTo(ComplaintStatus next, String note) {
        this.status = next;
        LocalDateTime now = LocalDateTime.now();
        if (next == ComplaintStatus.RESOLVED) {
            this.resolvedAt = now;
            this.resolution = note;
        } else if (next == ComplaintStatus.CLOSED) {
            this.closedAt = now;
        } else if (next == ComplaintStatus.IN_PROGRESS) {
            this.resolvedAt = null;
        }
        touch();
    }

    public void rate(int rating, String feedback) {
        this.rating = rating;
        this.feedback = feedback;
        touch();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** Used by the demo data. */
    public void forceState(ComplaintStatus status, LocalDateTime createdAt, LocalDateTime resolvedAt, LocalDateTime closedAt, String resolution) {
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
        this.closedAt = closedAt;
        this.resolution = resolution;
        this.updatedAt = closedAt != null ? closedAt : resolvedAt != null ? resolvedAt : createdAt.plusHours(2);
    }

    public Long getId() {
        return id;
    }

    public String getSubject() {
        return subject;
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

    public ComplaintStatus getStatus() {
        return status;
    }

    public User getCustomer() {
        return customer;
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

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public String getResolution() {
        return resolution;
    }

    public Integer getRating() {
        return rating;
    }

    public String getFeedback() {
        return feedback;
    }
}
