package com.icc.assignment.controller;

import com.icc.assignment.dto.ResolveResponse;
import com.icc.assignment.service.AssignmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints. The gateway has no route for /internal, so only other services reach these. */
@RestController
@RequestMapping("/internal/assignment")
public class InternalAssignmentController {

    private final AssignmentService assignmentService;

    public InternalAssignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping("/resolve")
    public ResolveResponse resolve(@RequestParam String category) {
        return assignmentService.resolveTeam(category);
    }
}
