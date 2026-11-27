package com.hackathon.vehicle.controller;

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

import com.hackathon.vehicle.dto.BookingRequest;
import com.hackathon.vehicle.dto.BookingResponse;
import com.hackathon.vehicle.dto.DayLoad;
import com.hackathon.vehicle.dto.ServiceTypeResponse;
import com.hackathon.vehicle.dto.VehicleBody;
import com.hackathon.vehicle.dto.VehicleResponse;
import com.hackathon.vehicle.service.GarageService;

import jakarta.validation.Valid;

/** Customer endpoints (the catalogue is open to any signed-in user): vehicles, services, free dates and bookings. */
@RestController
@RequestMapping("/api")
public class CustomerController {

    private final GarageService garage;

    public CustomerController(GarageService garage) {
        this.garage = garage;
    }

    @GetMapping("/catalog/services")
    public List<ServiceTypeResponse> services(@RequestParam(required = false) String vehicleType) {
        return garage.catalogue(vehicleType);
    }

    @GetMapping("/catalog/availability")
    public List<DayLoad> availability(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                      @RequestParam(required = false) Integer days) {
        return garage.availability(from, days);
    }

    @PostMapping("/vehicles")
    public ResponseEntity<VehicleResponse> register(@Valid @RequestBody VehicleBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(garage.registerVehicle(authentication.getName(), body));
    }

    @GetMapping("/vehicles")
    public List<VehicleResponse> vehicles(Authentication authentication) {
        return garage.myVehicles(authentication.getName());
    }

    @DeleteMapping("/vehicles/{id}")
    public ResponseEntity<Void> remove(@PathVariable Long id, Authentication authentication) {
        garage.deleteVehicle(authentication.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/vehicles/{id}/history")
    public List<BookingResponse> history(@PathVariable Long id, Authentication authentication) {
        return garage.history(authentication.getName(), id);
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> book(@Valid @RequestBody BookingRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(garage.book(authentication.getName(), body));
    }

    @GetMapping("/bookings/my")
    public List<BookingResponse> mine(@RequestParam(required = false) String scope, Authentication authentication) {
        return garage.mine(authentication.getName(), scope);
    }

    @DeleteMapping("/bookings/{id}")
    public BookingResponse cancel(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication authentication) {
        return garage.cancelOwn(authentication.getName(), id, reason);
    }
}
