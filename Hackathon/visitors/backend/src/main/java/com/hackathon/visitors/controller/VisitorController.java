package com.hackathon.visitors.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.visitors.dto.VisitResponse;
import com.hackathon.visitors.dto.VisitorRequest;
import com.hackathon.visitors.entity.VisitStatus;
import com.hackathon.visitors.service.VisitService;

import jakarta.validation.Valid;

/** Employee endpoints: invite visitors, follow them, change or cancel an invitation that has not been used. */
@RestController
@RequestMapping("/api/visitors")
public class VisitorController {

    private final VisitService service;

    public VisitorController(VisitService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<VisitResponse> invite(@Valid @RequestBody VisitorRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.invite(authentication.getName(), body));
    }

    @GetMapping("/my")
    public List<VisitResponse> mine(@RequestParam(required = false) VisitStatus status, @RequestParam(required = false) String search,
                                    Authentication authentication) {
        return service.mine(authentication.getName(), status, search);
    }

    @PutMapping("/{id}")
    public VisitResponse update(@PathVariable Long id, @Valid @RequestBody VisitorRequest body, Authentication authentication) {
        return service.update(id, authentication.getName(), body);
    }

    @DeleteMapping("/{id}")
    public VisitResponse cancel(@PathVariable Long id, Authentication authentication) {
        return service.cancel(id, authentication.getName());
    }
}
