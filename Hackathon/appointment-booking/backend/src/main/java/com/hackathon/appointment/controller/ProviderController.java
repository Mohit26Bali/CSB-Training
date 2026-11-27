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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.hackathon.appointment.dto.AppointmentResponse;
import com.hackathon.appointment.dto.BulkSlotBody;
import com.hackathon.appointment.dto.ProfileBody;
import com.hackathon.appointment.dto.ProviderDashboard;
import com.hackathon.appointment.dto.ProviderResponse;
import com.hackathon.appointment.dto.ReasonBody;
import com.hackathon.appointment.dto.SlotBody;
import com.hackathon.appointment.dto.SlotResponse;
import com.hackathon.appointment.entity.AppointmentStatus;
import com.hackathon.appointment.service.AppointmentService;

import jakarta.validation.Valid;

/** Provider endpoints: own slots (single and in bulk), the appointments on them, the profile and the dashboard. */
@RestController
@RequestMapping("/api/provider")
public class ProviderController {

    private final AppointmentService service;

    public ProviderController(AppointmentService service) {
        this.service = service;
    }

    @GetMapping("/slots")
    public List<SlotResponse> slots(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                    @RequestParam(required = false) Integer days, Authentication authentication) {
        return service.mySlots(authentication.getName(), from, days);
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotResponse> create(@Valid @RequestBody SlotBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createSlot(authentication.getName(), body));
    }

    @PostMapping("/slots/bulk")
    public ResponseEntity<List<SlotResponse>> bulk(@Valid @RequestBody BulkSlotBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createBulk(authentication.getName(), body));
    }

    @DeleteMapping("/slots/{id}")
    public ResponseEntity<Void> close(@PathVariable Long id, Authentication authentication) {
        service.closeSlot(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> appointments(@RequestParam(required = false) AppointmentStatus status,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                                  @RequestParam(required = false) String search, Authentication authentication) {
        return service.appointments(authentication.getName(), status, date, search);
    }

    @PutMapping("/appointments/{id}/confirm")
    public AppointmentResponse confirm(@PathVariable Long id, Authentication authentication) {
        return service.confirm(authentication.getName(), id);
    }

    @PutMapping("/appointments/{id}/cancel")
    public AppointmentResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonBody body, Authentication authentication) {
        return service.cancelAsProvider(authentication.getName(), id, body.reason());
    }

    @GetMapping("/profile")
    public ProviderResponse profile(Authentication authentication) {
        return service.profile(authentication.getName());
    }

    @PutMapping("/profile")
    public ProviderResponse updateProfile(@Valid @RequestBody ProfileBody body, Authentication authentication) {
        return service.updateProfile(authentication.getName(), body);
    }

    @GetMapping("/dashboard")
    public ProviderDashboard dashboard(Authentication authentication) {
        return service.dashboard(authentication.getName());
    }
}
