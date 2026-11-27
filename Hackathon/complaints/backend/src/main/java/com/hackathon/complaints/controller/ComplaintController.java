package com.hackathon.complaints.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.complaints.dto.ComplaintDetail;
import com.hackathon.complaints.dto.ComplaintRequest;
import com.hackathon.complaints.dto.ComplaintResponse;
import com.hackathon.complaints.dto.FeedbackRequest;
import com.hackathon.complaints.entity.ComplaintStatus;
import com.hackathon.complaints.service.ComplaintService;

import jakarta.validation.Valid;

/** Endpoints for everyone signed in; the service decides what each role may do with a complaint. */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final ComplaintService service;

    public ComplaintController(ComplaintService service) {
        this.service = service;
    }

    static boolean isAgent(Authentication authentication) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPPORT_AGENT"));
    }

    @PostMapping
    public ResponseEntity<ComplaintResponse> file(@Valid @RequestBody ComplaintRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.file(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<ComplaintResponse> mine(@RequestParam(required = false) ComplaintStatus status, @RequestParam(required = false) String search,
                                        Authentication authentication) {
        return service.mine(authentication.getName(), status, search);
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return service.meta();
    }

    @GetMapping("/{id}")
    public ComplaintDetail detail(@PathVariable Long id, Authentication authentication) {
        return service.detail(id, authentication.getName(), isAgent(authentication));
    }

    @PostMapping("/{id}/feedback")
    public ComplaintResponse feedback(@PathVariable Long id, @Valid @RequestBody FeedbackRequest body, Authentication authentication) {
        return service.feedback(id, authentication.getName(), body.rating(), body.comment());
    }
}
