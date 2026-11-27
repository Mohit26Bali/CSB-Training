package com.hackathon.enrollment.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.enrollment.dto.EnrollmentResponse;
import com.hackathon.enrollment.entity.EnrollmentStatus;
import com.hackathon.enrollment.service.EnrollmentService;

@RestController
@RequestMapping("/api/admin/enrollments")
public class AdminEnrollmentController {

    private final EnrollmentService enrollmentService;

    public AdminEnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public List<EnrollmentResponse> getEnrollments(
            @RequestParam(required = false) EnrollmentStatus status) {
        return enrollmentService.getAllEnrollments(status);
    }

    @PutMapping("/{id}/approve")
    public EnrollmentResponse approve(@PathVariable Long id, Authentication authentication) {
        return enrollmentService.approve(id, authentication.getName());
    }

    @PutMapping("/{id}/reject")
    public EnrollmentResponse reject(@PathVariable Long id, Authentication authentication) {
        return enrollmentService.reject(id, authentication.getName());
    }
}
