package com.hackathon.enrollment.dto;

import com.hackathon.enrollment.entity.EnrollmentStatus;

/** One row of the grouped enrollment count: how many enrollments a course has in a status. */
public record CourseStatusCount(Long courseId, EnrollmentStatus status, long total) {
}
