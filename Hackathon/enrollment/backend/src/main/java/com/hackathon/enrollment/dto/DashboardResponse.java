package com.hackathon.enrollment.dto;

import java.util.List;

public class DashboardResponse {

    private final long totalCourses;
    private final long activeCourses;
    private final long totalEmployees;
    private final long pending;
    private final long approved;
    private final long rejected;
    private final List<CourseResponse> courses;

    public DashboardResponse(long totalCourses, long activeCourses, long totalEmployees,
                             long pending, long approved, long rejected, List<CourseResponse> courses) {
        this.totalCourses = totalCourses;
        this.activeCourses = activeCourses;
        this.totalEmployees = totalEmployees;
        this.pending = pending;
        this.approved = approved;
        this.rejected = rejected;
        this.courses = courses;
    }

    public long getTotalCourses() {
        return totalCourses;
    }

    public long getActiveCourses() {
        return activeCourses;
    }

    public long getTotalEmployees() {
        return totalEmployees;
    }

    public long getPending() {
        return pending;
    }

    public long getApproved() {
        return approved;
    }

    public long getRejected() {
        return rejected;
    }

    /** Every course with its approved and pending counts, for the fill chart. */
    public List<CourseResponse> getCourses() {
        return courses;
    }
}
