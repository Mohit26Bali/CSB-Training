package com.hackathon.parking.controller;

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

import com.hackathon.parking.dto.AllocationResponse;
import com.hackathon.parking.dto.AskBody;
import com.hackathon.parking.dto.RequestResponse;
import com.hackathon.parking.dto.SlotResponse;
import com.hackathon.parking.service.ParkingService;

import jakarta.validation.Valid;

/** Employee endpoints: see free slots, ask for one, follow the request and see the allocation. */
@RestController
@RequestMapping("/api")
public class ParkingController {

    private final ParkingService parking;

    public ParkingController(ParkingService parking) {
        this.parking = parking;
    }

    @GetMapping("/slots")
    public List<SlotResponse> free(@RequestParam(required = false) Integer floor) {
        return parking.freeSlots(floor);
    }

    @GetMapping("/slots/floors")
    public List<Integer> floors() {
        return parking.floors();
    }

    @PostMapping("/parking/requests")
    public ResponseEntity<RequestResponse> ask(@Valid @RequestBody AskBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(parking.ask(authentication.getName(), body));
    }

    @GetMapping("/parking/requests/my")
    public List<RequestResponse> mine(Authentication authentication) {
        return parking.myRequests(authentication.getName());
    }

    @DeleteMapping("/parking/requests/{id}")
    public RequestResponse cancel(@PathVariable Long id, Authentication authentication) {
        return parking.cancel(authentication.getName(), id);
    }

    @GetMapping("/parking/allocation/my")
    public ResponseEntity<AllocationResponse> allocation(Authentication authentication) {
        AllocationResponse current = parking.myAllocation(authentication.getName());
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }
}
