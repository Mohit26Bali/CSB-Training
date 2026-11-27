package com.hackathon.scholarship.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.scholarship.dto.ApplicationRequest;
import com.hackathon.scholarship.dto.ApplicationResponse;
import com.hackathon.scholarship.service.ApplicationService;

import jakarta.validation.Valid;

/** Student endpoints: a student only ever applies and reads as themselves. */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    private final ApplicationService service;

    public ApplicationController(ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ApplicationResponse> apply(@Valid @RequestBody ApplicationRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.apply(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<ApplicationResponse> mine(Authentication authentication) {
        return service.myApplications(authentication.getName());
    }
}
