package com.hackathon.hotel.dto;

import java.time.LocalDate;

public record Span(Long bookingId, String number, String status, LocalDate checkIn, LocalDate checkOut, String customerName) {
}
