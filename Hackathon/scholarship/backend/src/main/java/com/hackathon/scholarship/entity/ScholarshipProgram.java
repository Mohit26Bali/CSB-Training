package com.hackathon.scholarship.entity;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "scholarship_programs", uniqueConstraints = @UniqueConstraint(columnNames = "name"))
public class ScholarshipProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    /** What an approved student receives. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate deadline;

    /** Minimum CGPA (out of 10) to meet the criteria. */
    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal minGpa;

    /** Highest annual family income that still meets the criteria. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal maxFamilyIncome;

    @Column(nullable = false)
    private boolean active = true;

    protected ScholarshipProgram() {
    }

    public ScholarshipProgram(String name, String description, BigDecimal amount, LocalDate deadline,
                              BigDecimal minGpa, BigDecimal maxFamilyIncome) {
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.deadline = deadline;
        this.minGpa = minGpa;
        this.maxFamilyIncome = maxFamilyIncome;
    }

    /** Open for applications: switched on and the deadline (inclusive) has not passed. */
    public boolean isOpen(LocalDate today) {
        return active && !deadline.isBefore(today);
    }

    public void update(String name, String description, BigDecimal amount, LocalDate deadline, BigDecimal minGpa,
                       BigDecimal maxFamilyIncome, boolean active) {
        this.name = name;
        this.description = description;
        this.amount = amount;
        this.deadline = deadline;
        this.minGpa = minGpa;
        this.maxFamilyIncome = maxFamilyIncome;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDate getDeadline() {
        return deadline;
    }

    public BigDecimal getMinGpa() {
        return minGpa;
    }

    public BigDecimal getMaxFamilyIncome() {
        return maxFamilyIncome;
    }

    public boolean isActive() {
        return active;
    }
}
