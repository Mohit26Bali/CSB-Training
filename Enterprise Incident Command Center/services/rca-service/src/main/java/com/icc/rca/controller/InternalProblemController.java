package com.icc.rca.controller;

import com.icc.rca.dto.ProblemStatsResponse;
import com.icc.rca.service.ProblemService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoint. The gateway has no route for /internal. */
@RestController
@RequestMapping("/internal/problems")
public class InternalProblemController {

    private final ProblemService service;

    public InternalProblemController(ProblemService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ProblemStatsResponse stats() {
        return service.stats();
    }
}
