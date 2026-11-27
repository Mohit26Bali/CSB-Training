package com.hackathon.event.controller;

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

import com.hackathon.event.dto.EventDashboard;
import com.hackathon.event.dto.EventRequest;
import com.hackathon.event.dto.EventResponse;
import com.hackathon.event.dto.RegistrationResponse;
import com.hackathon.event.entity.EventStatus;
import com.hackathon.event.service.EventService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final EventService events;

    public AdminController(EventService events) {
        this.events = events;
    }

    @GetMapping("/events")
    public List<EventResponse> list(@RequestParam(required = false) EventStatus status) {
        return events.listAll(status);
    }

    @PostMapping("/events")
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(events.create(body, authentication.getName()));
    }

    @PutMapping("/events/{id}")
    public EventResponse update(@PathVariable Long id, @Valid @RequestBody EventRequest body) {
        return events.update(id, body);
    }

    @PutMapping("/events/{id}/publish")
    public EventResponse publish(@PathVariable Long id) {
        return events.publish(id);
    }

    @PutMapping("/events/{id}/cancel")
    public EventResponse cancel(@PathVariable Long id) {
        return events.cancel(id);
    }

    @GetMapping("/events/{id}/participants")
    public List<RegistrationResponse> participants(@PathVariable Long id) {
        return events.participants(id);
    }

    @GetMapping("/dashboard")
    public EventDashboard dashboard() {
        return events.dashboard();
    }
}
