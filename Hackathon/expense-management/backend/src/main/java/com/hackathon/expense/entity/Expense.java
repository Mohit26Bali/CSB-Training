package com.hackathon.expense.entity;

import java.math.BigDecimal;
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
@Table(name = "expenses", indexes = {
        @Index(name = "idx_expense_status", columnList = "status"),
        @Index(name = "idx_expense_employee", columnList = "employee_id") })
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private User employee;

    @Column(nullable = false)
    private LocalDate expenseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExpenseCategory category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ExpenseStatus status = ExpenseStatus.PENDING;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 255)
    private String rejectionReason;

    protected Expense() {
    }

    public Expense(User employee, LocalDate expenseDate, ExpenseCategory category, BigDecimal amount,
                   String description) {
        this.employee = employee;
        this.expenseDate = expenseDate;
        this.category = category;
        this.amount = amount;
        this.description = description;
    }

    /** Records the final decision. The service guarantees this is only called on a PENDING expense. */
    public void decide(ExpenseStatus decision, String managerUsername, String reason) {
        this.status = decision;
        this.decidedBy = managerUsername;
        this.decidedAt = LocalDateTime.now();
        this.rejectionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public User getEmployee() {
        return employee;
    }

    public LocalDate getExpenseDate() {
        return expenseDate;
    }

    public ExpenseCategory getCategory() {
        return category;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public ExpenseStatus getStatus() {
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
}
