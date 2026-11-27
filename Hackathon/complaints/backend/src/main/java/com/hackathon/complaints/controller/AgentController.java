package com.hackathon.complaints.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.complaints.dto.AgentResponse;
import com.hackathon.complaints.dto.AssignRequest;
import com.hackathon.complaints.dto.ComplaintResponse;
import com.hackathon.complaints.dto.Statistics;
import com.hackathon.complaints.dto.StatusRequest;
import com.hackathon.complaints.entity.Category;
import com.hackathon.complaints.entity.ComplaintStatus;
import com.hackathon.complaints.entity.Priority;
import com.hackathon.complaints.service.ComplaintService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ComplaintService service;

    public AgentController(ComplaintService service) {
        this.service = service;
    }

    @GetMapping("/complaints")
    public List<ComplaintResponse> queue(@RequestParam(required = false) ComplaintStatus status, @RequestParam(required = false) Priority priority,
                                         @RequestParam(required = false) Category category, @RequestParam(required = false) String assignee,
                                         @RequestParam(required = false) String search, Authentication authentication) {
        return service.queue(authentication.getName(), status, priority, category, assignee, search);
    }

    @PutMapping("/complaints/{id}/assign")
    public ComplaintResponse assign(@PathVariable Long id, @RequestBody AssignRequest body, Authentication authentication) {
        return service.assign(id, body.assignee(), authentication.getName());
    }

    @PutMapping("/complaints/{id}/status")
    public ComplaintResponse status(@PathVariable Long id, @Valid @RequestBody StatusRequest body, Authentication authentication) {
        return service.updateStatus(id, body.status(), body.note(), authentication.getName());
    }

    @GetMapping("/agents")
    public List<AgentResponse> agents() {
        return service.agents();
    }

    @GetMapping("/statistics")
    public Statistics statistics(Authentication authentication) {
        return service.statistics(authentication.getName());
    }
}
