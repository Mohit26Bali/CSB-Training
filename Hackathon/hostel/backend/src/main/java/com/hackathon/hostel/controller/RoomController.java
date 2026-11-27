package com.hackathon.hostel.controller;

import java.util.Arrays;
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

import com.hackathon.hostel.dto.AllocationResponse;
import com.hackathon.hostel.dto.RequestResponse;
import com.hackathon.hostel.dto.RoomAskBody;
import com.hackathon.hostel.dto.RoomResponse;
import com.hackathon.hostel.entity.RoomType;
import com.hackathon.hostel.service.HostelService;
import com.hackathon.hostel.service.RoomService;

import jakarta.validation.Valid;

/** Student endpoints: see free rooms, ask for one, follow the request and see the allocation. */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final RoomService rooms;
    private final HostelService hostel;

    public RoomController(RoomService rooms, HostelService hostel) {
        this.rooms = rooms;
        this.hostel = hostel;
    }

    @GetMapping("/rooms")
    public List<RoomResponse> available(@RequestParam(required = false) String type) {
        return rooms.available(type);
    }

    @GetMapping("/rooms/types")
    public List<String> types() {
        return Arrays.stream(RoomType.values()).map(Enum::name).toList();
    }

    @PostMapping("/requests")
    public ResponseEntity<RequestResponse> ask(@Valid @RequestBody RoomAskBody body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hostel.ask(authentication.getName(), body));
    }

    @GetMapping("/requests/my")
    public List<RequestResponse> mine(Authentication authentication) {
        return hostel.myRequests(authentication.getName());
    }

    @DeleteMapping("/requests/{id}")
    public RequestResponse cancel(@PathVariable Long id, Authentication authentication) {
        return hostel.cancel(authentication.getName(), id);
    }

    @GetMapping("/allocation/my")
    public ResponseEntity<AllocationResponse> allocation(Authentication authentication) {
        AllocationResponse current = hostel.myAllocation(authentication.getName());
        return current == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(current);
    }
}
