package com.hackathon.enrollment.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.enrollment.dto.CourseRequest;
import com.hackathon.enrollment.dto.CourseResponse;
import com.hackathon.enrollment.dto.MessageResponse;
import com.hackathon.enrollment.service.CourseService;
import com.hackathon.enrollment.service.EnrollmentService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/api/admin/courses")
public class AdminCourseController {

    private final CourseService courseService;
    private final EnrollmentService enrollmentService;

    public AdminCourseController(CourseService courseService, EnrollmentService enrollmentService) {
        this.courseService = courseService;
        this.enrollmentService = enrollmentService;
    }

    @GetMapping
    public List<CourseResponse> getAllCourses(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) String category) {
        return courseService.getAllCourses(search, category);
    }

    @PostMapping
    public ResponseEntity<CourseResponse> createCourse(@Valid @RequestBody CourseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(courseService.createCourse(request));
    }

    @PutMapping("/{id}")
    public CourseResponse updateCourse(@PathVariable Long id,
                                       @Valid @RequestBody CourseRequest request) {
        return courseService.updateCourse(id, request);
    }

    @DeleteMapping("/{id}")
    public MessageResponse deleteCourse(@PathVariable Long id) {
        return courseService.deleteCourse(id);
    }

    @PostMapping("/{id}/reject-pending")
    public MessageResponse rejectPending(@PathVariable Long id, Authentication authentication) {
        return enrollmentService.rejectAllPending(id, authentication.getName());
    }
}
