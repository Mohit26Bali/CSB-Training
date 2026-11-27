package com.hackathon.enrollment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.enrollment.dto.EnrollmentRequest;
import com.hackathon.enrollment.dto.EnrollmentResponse;
import com.hackathon.enrollment.dto.MessageResponse;
import com.hackathon.enrollment.service.EnrollmentService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/enrollments")
public class EnrollmentController {

    private final EnrollmentService enrollmentService;

    public EnrollmentController(EnrollmentService enrollmentService) {
        this.enrollmentService = enrollmentService;
    }

    @GetMapping("/my")
    public List<EnrollmentResponse> myEnrollments(Authentication authentication) {
        return enrollmentService.getMyEnrollments(authentication.getName());
    }

    @PostMapping
    public ResponseEntity<EnrollmentResponse> requestEnrollment(
            @Valid @RequestBody EnrollmentRequest request, Authentication authentication) {
        EnrollmentResponse response =
                enrollmentService.requestEnrollment(authentication.getName(), request.getCourseId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public MessageResponse cancel(@PathVariable Long id, Authentication authentication) {
        return enrollmentService.cancel(authentication.getName(), id);
    }
}
