package com.hackathon.procurement.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.procurement.dto.PurchaseResponse;
import com.hackathon.procurement.dto.RequestSubmission;
import com.hackathon.procurement.dto.StatusTotal;
import com.hackathon.procurement.entity.RequestStatus;
import com.hackathon.procurement.service.ProcurementService;

import jakarta.validation.Valid;

/** Employee endpoints: an employee only ever creates and sees their own requests. */
@RestController
@RequestMapping("/api/requests")
public class RequestController {

    private final ProcurementService service;

    public RequestController(ProcurementService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    public List<Map<String, String>> categories() {
        return service.categories();
    }

    @PostMapping
    public ResponseEntity<PurchaseResponse> submit(@Valid @RequestBody RequestSubmission body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.submit(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<PurchaseResponse> mine(@RequestParam(required = false) RequestStatus status,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String search, Authentication authentication) {
        return service.myRequests(authentication.getName(), status, category, search);
    }

    @GetMapping("/my/summary")
    public List<StatusTotal> summary(Authentication authentication) {
        return service.mySummary(authentication.getName());
    }
}
