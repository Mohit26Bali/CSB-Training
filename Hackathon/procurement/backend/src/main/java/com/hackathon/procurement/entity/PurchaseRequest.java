package com.hackathon.procurement.entity;

import java.math.BigDecimal;
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
@Table(name = "purchase_requests", indexes = {
        @Index(name = "idx_pr_status", columnList = "status"),
        @Index(name = "idx_pr_employee", columnList = "employee_id") })
public class PurchaseRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(nullable = false, length = 120)
    private String itemName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ItemCategory category;

    @Column(nullable = false)
    private int quantity;

    /** Estimated cost of ONE unit. The total is quantity x this. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal estimatedUnitCost;

    @Column(nullable = false, length = 500)
    private String justification;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private RequestStatus status = RequestStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 255)
    private String rejectionReason;

    @Column(length = 40)
    private String orderReference;

    private LocalDateTime orderedAt;

    private LocalDateTime deliveredAt;

    protected PurchaseRequest() {
    }

    public PurchaseRequest(User employee, String itemName, ItemCategory category, int quantity,
                           BigDecimal estimatedUnitCost, String justification) {
        this.employee = employee;
        this.itemName = itemName;
        this.category = category;
        this.quantity = quantity;
        this.estimatedUnitCost = estimatedUnitCost;
        this.justification = justification;
    }

    public BigDecimal getEstimatedTotal() {
        return estimatedUnitCost.multiply(BigDecimal.valueOf(quantity));
    }

    public void approve(String manager) {
        this.status = RequestStatus.APPROVED;
        this.decidedBy = manager;
        this.decidedAt = LocalDateTime.now();
    }

    public void reject(String manager, String reason) {
        this.status = RequestStatus.REJECTED;
        this.decidedBy = manager;
        this.decidedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public void markOrdered(String reference) {
        this.status = RequestStatus.ORDERED;
        this.orderReference = reference;
        this.orderedAt = LocalDateTime.now();
    }

    public void markDelivered() {
        this.status = RequestStatus.DELIVERED;
        this.deliveredAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getEmployee() {
        return employee;
    }

    public String getItemName() {
        return itemName;
    }

    public ItemCategory getCategory() {
        return category;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getEstimatedUnitCost() {
        return estimatedUnitCost;
    }

    public String getJustification() {
        return justification;
    }

    public RequestStatus getStatus() {
        return status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
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

    public String getOrderReference() {
        return orderReference;
    }

    public LocalDateTime getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(LocalDateTime orderedAt) {
        this.orderedAt = orderedAt;
    }

    public LocalDateTime getDeliveredAt() {
        return deliveredAt;
    }

    public void setDeliveredAt(LocalDateTime deliveredAt) {
        this.deliveredAt = deliveredAt;
    }
}
