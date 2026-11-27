package com.hackathon.referral.controller;

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

import com.hackathon.referral.dto.JobRequest;
import com.hackathon.referral.dto.JobResponse;
import com.hackathon.referral.dto.RecruiterDashboard;
import com.hackathon.referral.dto.ReferralResponse;
import com.hackathon.referral.dto.StatusUpdateRequest;
import com.hackathon.referral.entity.JobStatus;
import com.hackathon.referral.entity.ReferralStatus;
import com.hackathon.referral.service.JobService;
import com.hackathon.referral.service.ReferralService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/recruiter")
public class RecruiterController {

    private final JobService jobs;
    private final ReferralService referrals;

    public RecruiterController(JobService jobs, ReferralService referrals) {
        this.jobs = jobs;
        this.referrals = referrals;
    }

    @GetMapping("/jobs")
    public List<JobResponse> jobs(@RequestParam(required = false) JobStatus status) {
        return jobs.listAll(status);
    }

    @PostMapping("/jobs")
    public ResponseEntity<JobResponse> create(@Valid @RequestBody JobRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(jobs.create(body, authentication.getName()));
    }

    @PutMapping("/jobs/{id}")
    public JobResponse update(@PathVariable Long id, @Valid @RequestBody JobRequest body) {
        return jobs.update(id, body);
    }

    @PutMapping("/jobs/{id}/publish")
    public JobResponse publish(@PathVariable Long id) {
        return jobs.publish(id);
    }

    @PutMapping("/jobs/{id}/close")
    public JobResponse close(@PathVariable Long id) {
        return jobs.close(id);
    }

    @GetMapping("/referrals")
    public List<ReferralResponse> referrals(@RequestParam(required = false) Long jobId,
                                            @RequestParam(required = false) ReferralStatus status,
                                            @RequestParam(required = false) String search) {
        return referrals.list(jobId, status, search);
    }

    @PutMapping("/referrals/{id}/status")
    public ReferralResponse updateStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest body,
                                         Authentication authentication) {
        return referrals.updateStatus(id, authentication.getName(), body.status(), body.note());
    }

    @GetMapping("/dashboard")
    public RecruiterDashboard dashboard() {
        return referrals.dashboard();
    }
}
