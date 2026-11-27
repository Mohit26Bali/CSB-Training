package com.hackathon.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record SlotBody(
        @NotNull(message = "Date is required") LocalDate date,
        @NotNull(message = "Start time is required") LocalTime startTime,
        @NotNull(message = "Length is required") @Min(value = 15, message = "A slot is at least 15 minutes") @Max(value = 180, message = "A slot is at most 180 minutes") Integer minutes) {
}
