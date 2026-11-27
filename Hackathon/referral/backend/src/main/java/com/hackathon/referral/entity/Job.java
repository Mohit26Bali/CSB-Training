package com.hackathon.referral.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "jobs")
public class Job {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 80)
    private String department;

    @Column(nullable = false, length = 80)
    private String location;

    @Column(nullable = false, length = 2000)
    private String description;

    /** Comma-separated, e.g. "Java,Spring Boot,MySQL". Use getSkillList() to read it. */
    @Column(nullable = false, length = 300)
    private String skills;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private JobStatus status = JobStatus.DRAFT;

    @Column(nullable = false)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime publishedAt;

    protected Job() {
    }

    public Job(String title, String department, String location, String description, List<String> skills,
               String createdBy) {
        this.title = title;
        this.department = department;
        this.location = location;
        this.description = description;
        this.skills = String.join(",", skills);
        this.createdBy = createdBy;
    }

    public void update(String title, String department, String location, String description, List<String> skills) {
        this.title = title;
        this.department = department;
        this.location = location;
        this.description = description;
        this.skills = String.join(",", skills);
    }

    public void publish() {
        this.status = JobStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void close() {
        this.status = JobStatus.CLOSED;
    }

    public List<String> getSkillList() {
        return skills.isBlank() ? List.of()
                : Arrays.stream(skills.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDepartment() {
        return department;
    }

    public String getLocation() {
        return location;
    }

    public String getDescription() {
        return description;
    }

    public JobStatus getStatus() {
        return status;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }
}
