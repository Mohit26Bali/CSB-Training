package com.hackathon.appointment.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AppointmentRequest(
        @NotNull(message = "Choose a slot") Long slotId,
        @Size(max = 300, message = "Reason must be at most 300 characters") String reason) {
}
