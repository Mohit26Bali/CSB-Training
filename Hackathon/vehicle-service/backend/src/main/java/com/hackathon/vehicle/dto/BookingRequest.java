package com.hackathon.vehicle.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BookingRequest(
        @NotNull(message = "Choose a vehicle") Long vehicleId,
        @NotNull(message = "Choose a service") Long serviceTypeId,
        @NotNull(message = "Service date is required") LocalDate serviceDate,
        @Size(max = 300, message = "Notes must be at most 300 characters") String notes) {
}
