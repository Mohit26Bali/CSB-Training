package com.hackathon.vehicle.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ServiceTypeBody(
        @NotBlank(message = "Name is required") @Size(max = 60, message = "Name must be at most 60 characters") String name,
        @Size(max = 300, message = "Description must be at most 300 characters") String description,
        @NotNull(message = "Duration is required") @Min(value = 1, message = "At least 1 day") @Max(value = 5, message = "At most 5 days") Integer durationDays,
        @NotNull(message = "Price is required") @DecimalMin(value = "100.00", message = "Price must be at least 100") @DecimalMax(value = "200000.00", message = "Price must be at most 200000") BigDecimal basePrice,
        Boolean active) {
}
