package com.hackathon.maintenance.controller;

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

import com.hackathon.maintenance.dto.NewRequest;
import com.hackathon.maintenance.dto.RequestResponse;
import com.hackathon.maintenance.entity.RequestStatus;
import com.hackathon.maintenance.service.MaintenanceService;

import jakarta.validation.Valid;

/** Endpoints for everyone signed in; the service decides what each role may do with a request. */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final MaintenanceService service;

    public RequestController(MaintenanceService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<RequestResponse> raise(@Valid @RequestBody NewRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.raise(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<RequestResponse> mine(@RequestParam(required = false) RequestStatus status, @RequestParam(required = false) String search,
                                      Authentication authentication) {
        return service.mine(authentication.getName(), status, search);
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        return service.meta();
    }

    @GetMapping("/{id}")
    public RequestResponse detail(@PathVariable Long id, Authentication authentication) {
        boolean manager = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_FACILITY_MANAGER"));
        return service.detail(id, authentication.getName(), manager);
    }
}
