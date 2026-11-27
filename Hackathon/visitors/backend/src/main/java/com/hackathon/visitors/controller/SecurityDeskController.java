package com.hackathon.visitors.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.visitors.dto.DailyStats;
import com.hackathon.visitors.dto.VisitResponse;
import com.hackathon.visitors.entity.VisitStatus;
import com.hackathon.visitors.service.VisitService;

@RestController
@RequestMapping("/api/security")
public class SecurityDeskController {

    private final VisitService service;

    public SecurityDeskController(VisitService service) {
        this.service = service;
    }

    @GetMapping("/visitors")
    public List<VisitResponse> gateList(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                        @RequestParam(required = false) VisitStatus status, @RequestParam(required = false) String search) {
        return service.gateList(date, status, search);
    }

    @GetMapping("/pass/{code}")
    public VisitResponse byPass(@PathVariable String code) {
        return service.byPass(code);
    }

    @PutMapping("/visitors/{id}/check-in")
    public VisitResponse checkIn(@PathVariable Long id, Authentication authentication) {
        return service.checkIn(id, authentication.getName());
    }

    @PutMapping("/visitors/{id}/check-out")
    public VisitResponse checkOut(@PathVariable Long id, Authentication authentication) {
        return service.checkOut(id, authentication.getName());
    }

    @GetMapping("/stats")
    public DailyStats stats() {
        return service.stats();
    }
}
