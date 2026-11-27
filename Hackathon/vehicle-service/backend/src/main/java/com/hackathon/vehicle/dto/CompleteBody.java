package com.hackathon.vehicle.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

public record CompleteBody(
        @DecimalMin(value = "0.00", message = "Final cost cannot be negative") @DecimalMax(value = "500000.00", message = "Final cost is too high") BigDecimal finalCost,
        @Size(max = 500, message = "Work notes must be at most 500 characters") String workNotes) {
}
