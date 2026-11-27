package com.hackathon.enrollment.dto;

import java.time.LocalDateTime;

import com.hackathon.enrollment.entity.EnrollmentStatus;

public class EnrollmentResponse {

    private Long id;
    private Long courseId;
    private String courseTitle;
    private String employeeUsername;
    private String employeeName;
    private EnrollmentStatus status;
    private LocalDateTime requestedAt;
    private String decidedBy;
    private LocalDateTime decidedAt;

    public EnrollmentResponse(Long id, Long courseId, String courseTitle, String employeeUsername,
                              String employeeName, EnrollmentStatus status, LocalDateTime requestedAt,
                              String decidedBy, LocalDateTime decidedAt) {
        this.id = id;
        this.courseId = courseId;
        this.courseTitle = courseTitle;
        this.employeeUsername = employeeUsername;
        this.employeeName = employeeName;
        this.status = status;
        this.requestedAt = requestedAt;
        this.decidedBy = decidedBy;
        this.decidedAt = decidedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getCourseId() {
        return courseId;
    }

    public String getCourseTitle() {
        return courseTitle;
    }

    public String getEmployeeUsername() {
        return employeeUsername;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public EnrollmentStatus getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public String getDecidedBy() {
        return decidedBy;
    }

    public LocalDateTime getDecidedAt() {
        return decidedAt;
    }
}
