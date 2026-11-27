package com.hackathon.scholarship.entity;

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
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** One student's application to one scholarship. The unique constraint is the last line of defence against applying twice. */
@Entity
@Table(name = "applications", uniqueConstraints = @UniqueConstraint(columnNames = { "student_id", "program_id" }))
public class Application {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private ScholarshipProgram program;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal gpa;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal annualFamilyIncome;

    @Column(nullable = false, length = 100)
    private String course;

    @Column(nullable = false)
    private int yearOfStudy;

    @Column(nullable = false, length = 1000)
    private String statement;

    /** 0 to 100, worked out when the application is submitted (see EligibilityCalculator). */
    @Column(nullable = false)
    private int eligibilityScore;

    @Column(nullable = false)
    private boolean meetsCriteria;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ApplicationStatus status = ApplicationStatus.SUBMITTED;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private String decidedBy;

    private LocalDateTime decidedAt;

    @Column(length = 500)
    private String decisionReason;

    protected Application() {
    }

    public Application(User student, ScholarshipProgram program, BigDecimal gpa, BigDecimal annualFamilyIncome,
                       String course, int yearOfStudy, String statement, int eligibilityScore, boolean meetsCriteria) {
        this.student = student;
        this.program = program;
        this.gpa = gpa;
        this.annualFamilyIncome = annualFamilyIncome;
        this.course = course;
        this.yearOfStudy = yearOfStudy;
        this.statement = statement;
        this.eligibilityScore = eligibilityScore;
        this.meetsCriteria = meetsCriteria;
    }

    public void decide(ApplicationStatus decision, String adminUsername, String reason) {
        this.status = decision;
        this.decidedBy = adminUsername;
        this.decidedAt = LocalDateTime.now();
        this.decisionReason = reason;
    }

    public Long getId() {
        return id;
    }

    public User getStudent() {
        return student;
    }

    public ScholarshipProgram getProgram() {
        return program;
    }

    public BigDecimal getGpa() {
        return gpa;
    }

    public BigDecimal getAnnualFamilyIncome() {
        return annualFamilyIncome;
    }

    public String getCourse() {
        return course;
    }

    public int getYearOfStudy() {
        return yearOfStudy;
    }

    public String getStatement() {
        return statement;
    }

    public int getEligibilityScore() {
        return eligibilityScore;
    }

    public boolean isMeetsCriteria() {
        return meetsCriteria;
    }

    public ApplicationStatus getStatus() {
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

    public String getDecisionReason() {
        return decisionReason;
    }
}
