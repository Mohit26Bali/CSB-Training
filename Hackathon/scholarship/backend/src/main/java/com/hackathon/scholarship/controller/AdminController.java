package com.hackathon.scholarship.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.scholarship.dto.ApplicationResponse;
import com.hackathon.scholarship.dto.DecisionRequest;
import com.hackathon.scholarship.dto.ProgramRequest;
import com.hackathon.scholarship.dto.ProgramResponse;
import com.hackathon.scholarship.dto.Statistics;
import com.hackathon.scholarship.entity.ApplicationStatus;
import com.hackathon.scholarship.service.ApplicationService;
import com.hackathon.scholarship.service.ProgramService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ProgramService programs;
    private final ApplicationService applications;

    public AdminController(ProgramService programs, ApplicationService applications) {
        this.programs = programs;
        this.applications = applications;
    }

    @GetMapping("/programs")
    public List<ProgramResponse> programs() {
        return programs.listAll();
    }

    @PostMapping("/programs")
    public ResponseEntity<ProgramResponse> createProgram(@Valid @RequestBody ProgramRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(programs.create(body));
    }

    @PutMapping("/programs/{id}")
    public ProgramResponse updateProgram(@PathVariable Long id, @Valid @RequestBody ProgramRequest body) {
        return programs.update(id, body);
    }

    @GetMapping("/applications")
    public List<ApplicationResponse> applications(@RequestParam(required = false) Long programId,
                                                  @RequestParam(required = false) ApplicationStatus status,
                                                  @RequestParam(required = false) String search) {
        return applications.list(programId, status, search);
    }

    @PutMapping("/applications/{id}/approve")
    public ApplicationResponse approve(@PathVariable Long id, @Valid @RequestBody DecisionRequest body, Authentication authentication) {
        return applications.approve(id, authentication.getName(), body.reason());
    }

    @PutMapping("/applications/{id}/reject")
    public ApplicationResponse reject(@PathVariable Long id, @Valid @RequestBody DecisionRequest body, Authentication authentication) {
        return applications.reject(id, authentication.getName(), body.reason());
    }

    @GetMapping("/statistics")
    public Statistics statistics() {
        return applications.statistics();
    }
}
