package com.hackathon.maintenance.dto;

import jakarta.validation.constraints.NotNull;

public record AssignBody(@NotNull(message = "Choose a technician") Long technicianId) {
}
