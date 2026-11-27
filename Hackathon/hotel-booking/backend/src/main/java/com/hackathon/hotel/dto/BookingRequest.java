package com.hackathon.hotel.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotNull(message = "Choose a room") Long roomId,
        @NotNull(message = "Check-in date is required") LocalDate checkIn,
        @NotNull(message = "Check-out date is required") LocalDate checkOut,
        @NotNull(message = "Number of guests is required") @Min(value = 1, message = "At least 1 guest") @Max(value = 10, message = "At most 10 guests") Integer guests,
        @Size(max = 300, message = "Special requests must be at most 300 characters") String specialRequests) {
}
