package com.hackathon.visitors.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

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

/** One invitation: an employee expects a visitor on a day. The pass code is what the visitor shows at the gate. */
@Entity
@Table(name = "visits")
public class Visit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 12)
    private String passCode;

    @Column(nullable = false, length = 100)
    private String visitorName;

    /** Digits only, with a leading + kept if there was one. */
    @Column(nullable = false, length = 20)
    private String phone;

    @Column(length = 100)
    private String company;

    @Column(nullable = false, length = 200)
    private String purpose;

    @Column(nullable = false)
    private LocalDate expectedDate;

    private LocalTime expectedTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private VisitStatus status = VisitStatus.EXPECTED;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "host_id", nullable = false)
    private User host;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime checkedInAt;

    private LocalDateTime checkedOutAt;

    private String checkedInBy;

    private String checkedOutBy;

    protected Visit() {
    }

    public Visit(String passCode, String visitorName, String phone, String company, String purpose, LocalDate expectedDate,
                 LocalTime expectedTime, User host) {
        this.passCode = passCode;
        update(visitorName, phone, company, purpose, expectedDate, expectedTime);
        this.host = host;
    }

    public final void update(String visitorName, String phone, String company, String purpose, LocalDate expectedDate, LocalTime expectedTime) {
        this.visitorName = visitorName;
        this.phone = phone;
        this.company = company;
        this.purpose = purpose;
        this.expectedDate = expectedDate;
        this.expectedTime = expectedTime;
    }

    public void checkIn(String guard) {
        this.status = VisitStatus.CHECKED_IN;
        this.checkedInAt = LocalDateTime.now();
        this.checkedInBy = guard;
    }

    public void checkOut(String guard) {
        this.status = VisitStatus.CHECKED_OUT;
        this.checkedOutAt = LocalDateTime.now();
        this.checkedOutBy = guard;
    }

    public void cancel() {
        this.status = VisitStatus.CANCELLED;
    }

    /** Used by the demo data. */
    public void forceState(VisitStatus status, LocalDateTime createdAt, LocalDateTime in, LocalDateTime out, String guard) {
        this.status = status;
        this.createdAt = createdAt;
        this.checkedInAt = in;
        this.checkedOutAt = out;
        this.checkedInBy = in == null ? null : guard;
        this.checkedOutBy = out == null ? null : guard;
    }

    public Long getId() {
        return id;
    }

    public String getPassCode() {
        return passCode;
    }

    public String getVisitorName() {
        return visitorName;
    }

    public String getPhone() {
        return phone;
    }

    public String getCompany() {
        return company;
    }

    public String getPurpose() {
        return purpose;
    }

    public LocalDate getExpectedDate() {
        return expectedDate;
    }

    public LocalTime getExpectedTime() {
        return expectedTime;
    }

    public VisitStatus getStatus() {
        return status;
    }

    public User getHost() {
        return host;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getCheckedInAt() {
        return checkedInAt;
    }

    public LocalDateTime getCheckedOutAt() {
        return checkedOutAt;
    }

    public String getCheckedInBy() {
        return checkedInBy;
    }

    public String getCheckedOutBy() {
        return checkedOutBy;
    }
}
