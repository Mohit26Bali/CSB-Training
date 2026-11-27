package com.hackathon.hostel.controller;

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

import com.hackathon.hostel.dto.AllocationResponse;
import com.hackathon.hostel.dto.ApproveBody;
import com.hackathon.hostel.dto.HostelDashboard;
import com.hackathon.hostel.dto.RejectBody;
import com.hackathon.hostel.dto.RequestResponse;
import com.hackathon.hostel.dto.RoomRequestBody;
import com.hackathon.hostel.dto.RoomResponse;
import com.hackathon.hostel.entity.RequestStatus;
import com.hackathon.hostel.service.HostelService;
import com.hackathon.hostel.service.RoomService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final RoomService rooms;
    private final HostelService hostel;

    public AdminController(RoomService rooms, HostelService hostel) {
        this.rooms = rooms;
        this.hostel = hostel;
    }

    @GetMapping("/rooms")
    public List<RoomResponse> rooms(@RequestParam(required = false) String type) {
        return rooms.listAll(type);
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomRequestBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rooms.create(body));
    }

    @PutMapping("/rooms/{id}")
    public RoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody RoomRequestBody body) {
        return rooms.update(id, body);
    }

    @GetMapping("/requests")
    public List<RequestResponse> requests(@RequestParam(required = false) RequestStatus status) {
        return hostel.allRequests(status);
    }

    @PutMapping("/requests/{id}/approve")
    public RequestResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveBody body, Authentication authentication) {
        return hostel.approve(id, body.roomId(), authentication.getName());
    }

    @PutMapping("/requests/{id}/reject")
    public RequestResponse reject(@PathVariable Long id, @Valid @RequestBody RejectBody body, Authentication authentication) {
        return hostel.reject(id, body.reason(), authentication.getName());
    }

    @GetMapping("/allocations")
    public List<AllocationResponse> allocations(@RequestParam(defaultValue = "true") boolean activeOnly) {
        return hostel.allocations(activeOnly);
    }

    @PutMapping("/allocations/{id}/vacate")
    public AllocationResponse vacate(@PathVariable Long id) {
        return hostel.vacate(id);
    }

    @GetMapping("/dashboard")
    public HostelDashboard dashboard() {
        return hostel.dashboard();
    }
}
