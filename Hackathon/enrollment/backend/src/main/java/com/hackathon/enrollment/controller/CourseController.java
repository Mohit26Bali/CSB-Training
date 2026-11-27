package com.hackathon.enrollment.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.enrollment.dto.CourseResponse;
import com.hackathon.enrollment.service.CourseService;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private final CourseService courseService;

    public CourseController(CourseService courseService) {
        this.courseService = courseService;
    }

    @GetMapping
    public List<CourseResponse> browseCourses(@RequestParam(required = false) String search,
                                              @RequestParam(required = false) String category) {
        return courseService.getActiveCourses(search, category);
    }

    @GetMapping("/categories")
    public List<String> categories() {
        return courseService.getCategories();
    }
}
