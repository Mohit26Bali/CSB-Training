package com.hackathon.enrollment.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.enrollment.dto.CourseResponse;
import com.hackathon.enrollment.dto.CourseStatusCount;
import com.hackathon.enrollment.dto.DashboardResponse;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.entity.Role;
import com.hackathon.enrollment.repo.EnrollmentRepository;
import com.hackathon.enrollment.repo.UserRepository;

@Service
public class DashboardService {

    private final CourseService courseService;
    private final EnrollmentRepository enrollmentRepository;
    private final UserRepository userRepository;

    public DashboardService(CourseService courseService, EnrollmentRepository enrollmentRepository,
                            UserRepository userRepository) {
        this.courseService = courseService;
        this.enrollmentRepository = enrollmentRepository;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        List<CourseResponse> courses = courseService.getAllCourses(null, null);
        Map<EnrollmentStatus, Long> totals = enrollmentRepository.countGroupedByCourseAndStatus().stream()
                .collect(Collectors.groupingBy(CourseStatusCount::status,
                        Collectors.summingLong(CourseStatusCount::total)));
        long active = courses.stream().filter(CourseResponse::isActive).count();
        return new DashboardResponse(courses.size(), active, userRepository.countByRole(Role.EMPLOYEE),
                totals.getOrDefault(EnrollmentStatus.PENDING, 0L),
                totals.getOrDefault(EnrollmentStatus.APPROVED, 0L),
                totals.getOrDefault(EnrollmentStatus.REJECTED, 0L), courses);
    }
}
