package com.hackathon.event.controller;

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

import com.hackathon.event.dto.EventResponse;
import com.hackathon.event.dto.RegistrationRequest;
import com.hackathon.event.dto.RegistrationResponse;
import com.hackathon.event.service.EventService;
import com.hackathon.event.service.RegistrationService;

import jakarta.validation.Valid;

/** Employee endpoints: browse upcoming events and manage one's own registrations. */
@RestController
@RequestMapping("/api")
public class EventController {

    private final EventService events;
    private final RegistrationService registrations;

    public EventController(EventService events, RegistrationService registrations) {
        this.events = events;
        this.registrations = registrations;
    }

    @GetMapping("/events")
    public List<EventResponse> upcoming(@RequestParam(required = false) String search,
                                        @RequestParam(required = false) String category, Authentication authentication) {
        return events.upcoming(authentication.getName(), search, category);
    }

    @GetMapping("/events/categories")
    public List<String> categories() {
        return events.categories();
    }

    @PostMapping("/registrations")
    public ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(registrations.register(authentication.getName(), body));
    }

    @GetMapping("/registrations/my")
    public List<RegistrationResponse> mine(Authentication authentication) {
        return registrations.mine(authentication.getName());
    }

    @DeleteMapping("/registrations/{id}")
    public RegistrationResponse cancel(@PathVariable Long id, Authentication authentication) {
        return registrations.cancel(authentication.getName(), id);
    }
}
