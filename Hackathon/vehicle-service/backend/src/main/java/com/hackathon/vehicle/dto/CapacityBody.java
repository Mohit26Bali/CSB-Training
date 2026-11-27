package com.hackathon.vehicle.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CapacityBody(@NotNull(message = "Number of bays is required") @Min(value = 1, message = "At least 1 bay") @Max(value = 30, message = "At most 30 bays") Integer bays) {
}
