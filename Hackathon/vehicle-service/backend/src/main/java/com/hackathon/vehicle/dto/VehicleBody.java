package com.hackathon.vehicle.dto;

import com.hackathon.vehicle.entity.VehicleType;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VehicleBody(
        @NotBlank(message = "Vehicle number is required")
        @Size(max = 15, message = "Vehicle number must be at most 15 characters")
        @Pattern(regexp = "^[A-Za-z]{2}[ -]?\\d{1,2}[ -]?[A-Za-z]{0,3}[ -]?\\d{4}$", message = "Use the format MH12AB1234")
        String number,
        @NotNull(message = "Vehicle type is required") VehicleType type,
        @NotBlank(message = "Make is required") @Size(max = 40, message = "Make must be at most 40 characters") String make,
        @NotBlank(message = "Model is required") @Size(max = 40, message = "Model must be at most 40 characters") String model,
        @NotNull(message = "Year is required") @Min(value = 1990, message = "Year must be 1990 or later") @Max(value = 2100, message = "Enter a valid year") Integer year) {
}
