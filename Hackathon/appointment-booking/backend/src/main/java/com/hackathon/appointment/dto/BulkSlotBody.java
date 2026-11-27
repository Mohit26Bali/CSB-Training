package com.hackathon.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record BulkSlotBody(
        @NotNull(message = "Date is required") LocalDate date,
        @NotNull(message = "From time is required") LocalTime fromTime,
        @NotNull(message = "To time is required") LocalTime toTime,
        @NotNull(message = "Slot length is required") @Min(value = 15, message = "A slot is at least 15 minutes") @Max(value = 180, message = "A slot is at most 180 minutes") Integer minutes) {
}
