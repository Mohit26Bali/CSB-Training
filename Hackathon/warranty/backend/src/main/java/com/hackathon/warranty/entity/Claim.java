package com.hackathon.warranty.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "claims", indexes = {
        @Index(name = "idx_claim_product", columnList = "product_id"),
        @Index(name = "idx_claim_status", columnList = "status") })
public class Claim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @Column(nullable = false, length = 1000)
    private String issueDescription;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 6)
    private ClaimPriority priority = ClaimPriority.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ClaimStatus status = ClaimStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 255)
    private String rejectionReason;

    private LocalDateTime repairStartedAt;

    private LocalDateTime completedAt;

    @Column(length = 500)
    private String resolutionNote;

    protected Claim() {
    }

    public Claim(Product product, User customer, String issueDescription, ClaimPriority priority) {
        this.product = product;
        this.customer = customer;
        this.issueDescription = issueDescription;
        this.priority = priority;
    }

    public void approve(String agent) {
        this.status = ClaimStatus.APPROVED;
        this.decidedBy = agent;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(String agent, String reason) {
        this.status = ClaimStatus.REJECTED;
        this.decidedBy = agent;
        this.decidedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public void startRepair() {
        this.status = ClaimStatus.IN_REPAIR;
        this.repairStartedAt = LocalDateTime.now();
    }

    public void complete(String note) {
        this.status = ClaimStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.resolutionNote = note;
    }

    public Long getId() {
        return id;
    }

    public Product getProduct() {
        return product;
    }

    public User getCustomer() {
        return customer;
    }

    public String getIssueDescription() {
        return issueDescription;
    }

    public ClaimPriority getPriority() {
        return priority;
    }

    public ClaimStatus getStatus() {
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

    public String getRejectionReason() {
        return rejectionReason;
    }

    public LocalDateTime getRepairStartedAt() {
        return repairStartedAt;
    }

    public void setRepairStartedAt(LocalDateTime repairStartedAt) {
        this.repairStartedAt = repairStartedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getResolutionNote() {
        return resolutionNote;
    }
}
