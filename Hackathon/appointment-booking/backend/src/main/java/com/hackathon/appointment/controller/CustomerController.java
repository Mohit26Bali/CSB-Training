package com.hackathon.appointment.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.hackathon.appointment.dto.AppointmentRequest;
import com.hackathon.appointment.dto.AppointmentResponse;
import com.hackathon.appointment.dto.ProviderResponse;
import com.hackathon.appointment.dto.SlotResponse;
import com.hackathon.appointment.service.AppointmentService;

import jakarta.validation.Valid;

/** Customer endpoints (browsing providers and slots is open to any signed-in user): find a slot, book, follow and cancel. */
@RestController
@RequestMapping("/api")
public class CustomerController {

    private final AppointmentService service;

    public CustomerController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping("/providers")
    public List<ProviderResponse> providers(@RequestParam(required = false) String search) {
        return service.providers(search);
    }

    @GetMapping("/providers/{id}/slots")
    public List<SlotResponse> slots(@PathVariable Long id, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) Integer days) {
        return service.providerSlots(id, from, days);
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> book(@Valid @RequestBody AppointmentRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.book(authentication.getName(), body));
    }

    @GetMapping("/appointments/my")
    public List<AppointmentResponse> mine(@RequestParam(required = false) String scope, Authentication authentication) {
        return service.mine(authentication.getName(), scope);
    }

    @DeleteMapping("/appointments/{id}")
    public AppointmentResponse cancel(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication authentication) {
        return service.cancelOwn(authentication.getName(), id, reason);
    }
}
