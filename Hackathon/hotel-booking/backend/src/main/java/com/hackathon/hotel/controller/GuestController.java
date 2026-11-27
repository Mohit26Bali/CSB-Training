package com.hackathon.hotel.controller;

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

import com.hackathon.hotel.dto.BookingRequest;
import com.hackathon.hotel.dto.BookingResponse;
import com.hackathon.hotel.dto.ReasonBody;
import com.hackathon.hotel.dto.RoomResponse;
import com.hackathon.hotel.entity.BookingStatus;
import com.hackathon.hotel.service.HotelService;

import jakarta.validation.Valid;

/** Guest endpoints: search rooms for a stay, book, follow and cancel bookings. */
@RestController
@RequestMapping("/api")
public class GuestController {

    private final HotelService hotel;

    public GuestController(HotelService hotel) {
        this.hotel = hotel;
    }

    @GetMapping("/rooms/available")
    public List<RoomResponse> available(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkIn,
                                        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate checkOut,
                                        @RequestParam(required = false) String type, @RequestParam(required = false) Integer guests) {
        return hotel.available(checkIn, checkOut, type, guests);
    }

    @GetMapping("/rooms/types")
    public List<String> types() {
        return hotel.types();
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> book(@Valid @RequestBody BookingRequest body, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED).body(hotel.book(authentication.getName(), body));
    }

    @GetMapping("/bookings/my")
    public List<BookingResponse> mine(@RequestParam(required = false) BookingStatus status, Authentication authentication) {
        return hotel.mine(authentication.getName(), status);
    }

    @DeleteMapping("/bookings/{id}")
    public BookingResponse cancel(@PathVariable Long id, @RequestParam(required = false) String reason, Authentication authentication) {
        return hotel.cancelOwn(authentication.getName(), id, reason);
    }
}
