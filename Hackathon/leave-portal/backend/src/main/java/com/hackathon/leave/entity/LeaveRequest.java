package com.hackathon.leave.entity;

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
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "leave_requests", indexes = {
        @Index(name = "idx_leave_employee", columnList = "employee_id"),
        @Index(name = "idx_leave_status", columnList = "status") })
public class LeaveRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LeaveType leaveType;

    @Column(nullable = false)
    private LocalDate startDate;

    @Column(nullable = false)
    private LocalDate endDate;

    /** Working days (Monday to Friday) in the range, both ends included. */
    @Column(nullable = false)
    private int days;

    @Column(nullable = false, length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LeaveStatus status = LeaveStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 255)
    private String decisionNote;

    protected LeaveRequest() {
    }

    public LeaveRequest(User employee, LeaveType leaveType, LocalDate startDate, LocalDate endDate, int days,
                        String reason) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.startDate = startDate;
        this.endDate = endDate;
        this.days = days;
        this.reason = reason;
    }

    /** Records a manager's decision (approve or reject) with who made it and when. */
    public void decide(LeaveStatus decision, String managerUsername, String note) {
        this.status = decision;
        this.decidedBy = managerUsername;
        this.decidedAt = LocalDateTime.now();
        this.decisionNote = note;
    }

    public void cancel() {
        this.status = LeaveStatus.CANCELLED;
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !startDate.isAfter(to) && !endDate.isBefore(from);
    }

    public Long getId() {
        return id;
    }

    public User getEmployee() {
        return employee;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public int getDays() {
        return days;
    }

    public String getReason() {
        return reason;
    }

    public LeaveStatus getStatus() {
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

    public String getDecisionNote() {
        return decisionNote;
    }
}
