package com.hackathon.leave.entity;

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
import jakarta.persistence.UniqueConstraint;

/** One employee's allowance and approved usage for one leave type in one calendar year. */
@Entity
@Table(name = "leave_balances",
        uniqueConstraints = @UniqueConstraint(columnNames = { "user_id", "leave_year", "leave_type" }))
public class LeaveBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 10)
    private LeaveType leaveType;

    @Column(nullable = false)
    private int allocated;

    @Column(nullable = false)
    private int used;

    protected LeaveBalance() {
    }

    public LeaveBalance(User user, int leaveYear, LeaveType leaveType, int allocated) {
        this.user = user;
        this.leaveYear = leaveYear;
        this.leaveType = leaveType;
        this.allocated = allocated;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public int getLeaveYear() {
        return leaveYear;
    }

    public LeaveType getLeaveType() {
        return leaveType;
    }

    public int getAllocated() {
        return allocated;
    }

    public int getUsed() {
        return used;
    }

    public void setUsed(int used) {
        this.used = used;
    }
}
