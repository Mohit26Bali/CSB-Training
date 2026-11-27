package com.hackathon.hotel.controller;

import java.time.YearMonth;
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

import com.hackathon.hotel.dto.BookingResponse;
import com.hackathon.hotel.dto.CalendarResponse;
import com.hackathon.hotel.dto.HotelDashboard;
import com.hackathon.hotel.dto.ReasonBody;
import com.hackathon.hotel.dto.RoomBody;
import com.hackathon.hotel.dto.RoomResponse;
import com.hackathon.hotel.entity.BookingStatus;
import com.hackathon.hotel.service.HotelService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final HotelService hotel;

    public AdminController(HotelService hotel) {
        this.hotel = hotel;
    }

    @GetMapping("/rooms")
    public List<RoomResponse> rooms(@RequestParam(required = false) String type) {
        return hotel.allRooms(type);
    }

    @PostMapping("/rooms")
    public ResponseEntity<RoomResponse> createRoom(@Valid @RequestBody RoomBody body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotel.createRoom(body));
    }

    @PutMapping("/rooms/{id}")
    public RoomResponse updateRoom(@PathVariable Long id, @Valid @RequestBody RoomBody body) {
        return hotel.updateRoom(id, body);
    }

    @GetMapping("/bookings")
    public List<BookingResponse> bookings(@RequestParam(required = false) BookingStatus status, @RequestParam(required = false) Long roomId,
                                          @RequestParam(required = false) String search) {
        return hotel.allBookings(status, roomId, search);
    }

    @PutMapping("/bookings/{id}/confirm")
    public BookingResponse confirm(@PathVariable Long id, Authentication authentication) {
        return hotel.confirm(id, authentication.getName());
    }

    @PutMapping("/bookings/{id}/cancel")
    public BookingResponse cancel(@PathVariable Long id, @Valid @RequestBody ReasonBody body, Authentication authentication) {
        return hotel.adminCancel(id, body.reason(), authentication.getName());
    }

    @GetMapping("/calendar")
    public CalendarResponse calendar(@RequestParam(required = false) String month) {
        return hotel.calendar(month == null || month.isBlank() ? YearMonth.now() : YearMonth.parse(month));
    }

    @GetMapping("/dashboard")
    public HotelDashboard dashboard() {
        return hotel.dashboard();
    }
}
