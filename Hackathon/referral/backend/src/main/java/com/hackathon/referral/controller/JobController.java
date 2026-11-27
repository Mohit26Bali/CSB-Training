package com.hackathon.referral.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.referral.dto.JobResponse;
import com.hackathon.referral.service.JobService;

/** Any signed-in user can browse the published jobs. */
@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobService service;

    public JobController(JobService service) {
        this.service = service;
    }

    @GetMapping
    public List<JobResponse> open(@RequestParam(required = false) String search, @RequestParam(required = false) String skill) {
        return service.listOpen(search, skill);
    }

    @GetMapping("/skills")
    public List<String> skills() {
        return service.openSkills();
    }
}
