package com.hackathon.leave.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.leave.dto.BalanceResponse;
import com.hackathon.leave.dto.LeaveResponse;
import com.hackathon.leave.dto.LeaveSubmission;
import com.hackathon.leave.dto.TypeOption;
import com.hackathon.leave.entity.LeaveStatus;
import com.hackathon.leave.service.LeaveService;

import jakarta.validation.Valid;

/** Endpoints for anyone who takes leave (employees and managers): they only ever touch their own requests. */
@RestController
@RequestMapping("/api/leave")
public class LeaveController {

    private final LeaveService service;

    public LeaveController(LeaveService service) {
        this.service = service;
    }

    @GetMapping("/types")
    public List<TypeOption> types() {
        return service.types();
    }

    @GetMapping("/balance")
    public List<BalanceResponse> balance(@RequestParam(required = false) Integer year, Authentication authentication) {
        return service.myBalances(authentication.getName(), year);
    }

    @PostMapping
    public ResponseEntity<LeaveResponse> submit(@Valid @RequestBody LeaveSubmission body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<LeaveResponse> mine(@RequestParam(required = false) LeaveStatus status,
                                    @RequestParam(required = false) String type, Authentication authentication) {
        return service.myLeave(authentication.getName(), status, type);
    }

    @DeleteMapping("/{id}")
    public LeaveResponse cancel(@PathVariable Long id, Authentication authentication) {
        return service.cancel(authentication.getName(), id);
    }
}
