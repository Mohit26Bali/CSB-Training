package com.hackathon.enrollment.dto;

import jakarta.validation.constraints.NotNull;

public class EnrollmentRequest {

    @NotNull(message = "courseId is required")
    private Long courseId;

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }
}


