package com.hackathon.warranty.controller;

import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.warranty.dto.ClaimResponse;
import com.hackathon.warranty.dto.CompleteRequest;
import com.hackathon.warranty.dto.RejectRequest;
import com.hackathon.warranty.dto.StatusCount;
import com.hackathon.warranty.entity.ClaimStatus;
import com.hackathon.warranty.service.ClaimService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final ClaimService service;

    public AgentController(ClaimService service) {
        this.service = service;
    }

    @GetMapping("/claims")
    public List<ClaimResponse> list(@RequestParam(required = false) ClaimStatus status,
                                    @RequestParam(required = false) String priority,
                                    @RequestParam(required = false) String search) {
        return service.list(status, priority, search);
    }

    @GetMapping("/summary")
    public List<StatusCount> summary() {
        return service.summary();
    }

    @PutMapping("/claims/{id}/approve")
    public ClaimResponse approve(@PathVariable Long id, Authentication authentication) {
        return service.approve(id, authentication.getName());
    }

    @PutMapping("/claims/{id}/reject")
    public ClaimResponse reject(@PathVariable Long id, @Valid @RequestBody RejectRequest body, Authentication authentication) {
        return service.reject(id, authentication.getName(), body.reason());
    }

    @PutMapping("/claims/{id}/start-repair")
    public ClaimResponse startRepair(@PathVariable Long id) {
        return service.startRepair(id);
    }

    @PutMapping("/claims/{id}/complete")
    public ClaimResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteRequest body) {
        return service.complete(id, body == null ? null : body.resolutionNote());
    }
}
