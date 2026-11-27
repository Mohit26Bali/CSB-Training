package com.hackathon.parking.controller;

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

import com.hackathon.parking.dto.AllocationResponse;
import com.hackathon.parking.dto.ApproveBody;
import com.hackathon.parking.dto.ParkingDashboard;
import com.hackathon.parking.dto.RejectBody;
import com.hackathon.parking.dto.RequestResponse;
import com.hackathon.parking.dto.SlotBody;
import com.hackathon.parking.dto.SlotResponse;
import com.hackathon.parking.entity.RequestStatus;
import com.hackathon.parking.service.ParkingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final ParkingService parking;

    public AdminController(ParkingService parking) {
        this.parking = parking;
    }

    @GetMapping("/slots")
    public List<SlotResponse> slots(@RequestParam(required = false) Integer floor) {
        return parking.allSlots(floor);
    }

    @PostMapping("/slots")
    public ResponseEntity<SlotResponse> createSlot(@Valid @RequestBody SlotBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parking.createSlot(body));
    }

    @PutMapping("/slots/{id}")
    public SlotResponse updateSlot(@PathVariable Long id, @Valid @RequestBody SlotBody body) {
        return parking.updateSlot(id, body);
    }

    @GetMapping("/requests")
    public List<RequestResponse> requests(@RequestParam(required = false) RequestStatus status) {
        return parking.allRequests(status);
    }

    @PutMapping("/requests/{id}/approve")
    public RequestResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveBody body, Authentication authentication) {
        return parking.approve(id, body.slotId(), authentication.getName());
    }

    @PutMapping("/requests/{id}/reject")
    public RequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectBody body, Authentication authentication) {
        return parking.reject(id, body.reason(), authentication.getName());
    }

    @GetMapping("/allocations")
    public List<AllocationResponse> allocations(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return parking.allocations(activeOnly);
    }

    @PutMapping("/allocations/{id}/release")
    public AllocationResponse release(@PathVariable Long id, Authentication authentication) {
        return parking.release(id, authentication.getName());
    }

    @GetMapping("/dashboard")
    public ParkingDashboard dashboard() {
        return parking.dashboard();
    }
}
