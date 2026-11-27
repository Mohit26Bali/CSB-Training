package com.hackathon.vehicle.controller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.hackathon.vehicle.dto.BookingResponse;
import com.hackathon.vehicle.dto.CapacityBody;
import com.hackathon.vehicle.dto.CompleteBody;
import com.hackathon.vehicle.dto.ReasonBody;
import com.hackathon.vehicle.dto.ServiceDashboard;
import com.hackathon.vehicle.dto.ServiceTypeBody;
import com.hackathon.vehicle.dto.ServiceTypeResponse;
import com.hackathon.vehicle.dto.VehicleResponse;
import com.hackathon.vehicle.entity.BookingStatus;
import com.hackathon.vehicle.service.GarageService;

import jakarta.validation.Valid;

/** Service-center endpoints: the schedule, status changes, vehicle search, the service catalogue and the dashboard. */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final GarageService garage;

    public AdminController(GarageService garage) {
        this.garage = garage;
    }

    @GetMapping("/bookings")
    public List<BookingResponse> bookings(@RequestParam(required = false) BookingStatus status,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                          @RequestParam(required = false) String search) {
        return garage.allBookings(status, date, search);
    }

    @PutMapping("/bookings/{id}/start")
    public BookingResponse start(@PathVariable Long id, Authentication authentication) {
        return garage.start(authentication.getName(), id);
    }

    @PutMapping("/bookings/{id}/complete")
    public BookingResponse complete(@PathVariable Long id, @Valid @RequestBody(required = false) CompleteBody body, Authentication authentication) {
        return garage.complete(authentication.getName(), id, body == null ? new CompleteBody(null, null) : body);
    }

    @PutMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonBody body, Authentication authentication) {
        return garage.cancelAsAdmin(authentication.getName(), id, body.reason());
    }

    @GetMapping("/vehicles")
    public List<VehicleResponse> vehicles(@RequestParam(required = false) String search) {
        return garage.searchVehicles(search);
    }

    @GetMapping("/vehicles/{id}/history")
    public List<BookingResponse> history(@PathVariable Long id) {
        return garage.vehicleHistory(id);
    }

    @GetMapping("/services")
    public List<ServiceTypeResponse> services() {
        return garage.allServices();
    }

    @PostMapping("/services")
    public ResponseEntity<ServiceTypeResponse> create(@Valid @RequestBody ServiceTypeBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(garage.createService(body));
    }

    @PutMapping("/services/{id}")
    public ServiceTypeResponse update(@PathVariable Long id, @Valid @RequestBody ServiceTypeBody body) {
        return garage.updateService(id, body);
    }

    @GetMapping("/bays")
    public Map<String, Integer> bays() {
        return Map.of("bays", garage.baysNow());
    }

    @PutMapping("/bays")
    public Map<String, Integer> setBays(@Valid @RequestBody CapacityBody body) {
        return Map.of("bays", garage.setBays(body.bays()));
    }

    @GetMapping("/dashboard")
    public ServiceDashboard dashboard() {
        return garage.dashboard();
    }
}
