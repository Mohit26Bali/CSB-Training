package com.hackathon.asset.entity;

import java.time.LocalDate;
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

/** One employee holding one asset, from the day it was handed over until the return was accepted. The asset's history is its list of assignments. */
@Entity
@Table(name = "assignments")
public class Assignment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(nullable = false)
    private LocalDateTime assignedAt = LocalDateTime.now();

    @Column(nullable = false)
    private String assignedBy;

    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_out", nullable = false, length = 6)
    private AssetCondition conditionOut;

    @Column(length = 300)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AssignmentStatus status = AssignmentStatus.ASSIGNED;

    private LocalDateTime returnRequestedAt;

    @Column(length = 300)
    private String returnNote;

    private LocalDateTime returnedAt;

    private String receivedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_in", length = 6)
    private AssetCondition conditionIn;

    @Column(length = 300)
    private String receiveNote;

    protected Assignment() {
    }

    public Assignment(Asset asset, User employee, String assignedBy, LocalDate dueDate, String note) {
        this.asset = asset;
        this.employee = employee;
        this.assignedBy = assignedBy;
        this.dueDate = dueDate;
        this.conditionOut = asset.getCondition();
        this.note = note;
    }

    public void requestReturn(String note) {
        this.status = AssignmentStatus.RETURN_REQUESTED;
        this.returnRequestedAt = LocalDateTime.now();
        this.returnNote = note;
    }

    public void accept(String admin, AssetCondition condition, String note) {
        this.status = AssignmentStatus.RETURNED;
        this.returnedAt = LocalDateTime.now();
        this.receivedBy = admin;
        this.conditionIn = condition;
        this.receiveNote = note;
    }

    public boolean isActive() {
        return status != AssignmentStatus.RETURNED;
    }

    /** Used by the demo data. */
    public void forceState(LocalDateTime assignedAt, AssignmentStatus status, LocalDateTime requestedAt, String returnNote, LocalDateTime returnedAt,
                           String receivedBy, AssetCondition conditionIn, String receiveNote) {
        this.assignedAt = assignedAt;
        this.status = status;
        this.returnRequestedAt = requestedAt;
        this.returnNote = returnNote;
        this.returnedAt = returnedAt;
        this.receivedBy = receivedBy;
        this.conditionIn = conditionIn;
        this.receiveNote = receiveNote;
    }

    public Long getId() {
        return id;
    }

    public Asset getAsset() {
        return asset;
    }

    public User getEmployee() {
        return employee;
    }

    public LocalDateTime getAssignedAt() {
        return assignedAt;
    }

    public String getAssignedBy() {
        return assignedBy;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public AssetCondition getConditionOut() {
        return conditionOut;
    }

    public String getNote() {
        return note;
    }

    public AssignmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getReturnRequestedAt() {
        return returnRequestedAt;
    }

    public String getReturnNote() {
        return returnNote;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public String getReceivedBy() {
        return receivedBy;
    }

    public AssetCondition getConditionIn() {
        return conditionIn;
    }

    public String getReceiveNote() {
        return receiveNote;
    }
}
