package com.hackathon.referral.entity;

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

/** One candidate referred for one job. The unique constraint stops the same candidate being referred twice for a job. */
@Entity
@Table(name = "referrals", uniqueConstraints = @UniqueConstraint(columnNames = { "job_id", "candidate_email" }))
public class Referral {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "referrer_id", nullable = false)
    private User referrer;

    @Column(nullable = false, length = 100)
    private String candidateName;

    /** Stored in lower case, so "A@x.com" and "a@x.com" are the same candidate. */
    @Column(name = "candidate_email", nullable = false, length = 150)
    private String candidateEmail;

    @Column(length = 20)
    private String candidatePhone;

    @Column(length = 300)
    private String resumeUrl;

    @Column(length = 500)
    private String notes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ReferralStatus status = ReferralStatus.SUBMITTED;

    @Column(nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

    private String updatedBy;

    private LocalDateTime updatedAt;

    @Column(length = 255)
    private String statusNote;

    protected Referral() {
    }

    public Referral(Job job, User referrer, String candidateName, String candidateEmail, String candidatePhone,
                    String resumeUrl, String notes) {
        this.job = job;
        this.referrer = referrer;
        this.candidateName = candidateName;
        this.candidateEmail = candidateEmail;
        this.candidatePhone = candidatePhone;
        this.resumeUrl = resumeUrl;
        this.notes = notes;
    }

    public void moveTo(ReferralStatus next, String recruiter, String note) {
        this.status = next;
        this.updatedBy = recruiter;
        this.updatedAt = LocalDateTime.now();
        this.statusNote = note;
    }

    public Long getId() {
        return id;
    }

    public Job getJob() {
        return job;
    }

    public User getReferrer() {
        return referrer;
    }

    public String getCandidateName() {
        return candidateName;
    }

    public String getCandidateEmail() {
        return candidateEmail;
    }

    public String getCandidatePhone() {
        return candidatePhone;
    }

    public String getResumeUrl() {
        return resumeUrl;
    }

    public String getNotes() {
        return notes;
    }

    public ReferralStatus getStatus() {
        return status;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getStatusNote() {
        return statusNote;
    }
}
