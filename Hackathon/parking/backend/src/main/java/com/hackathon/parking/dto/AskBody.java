package com.hackathon.parking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An employee's request for a slot. */
public record AskBody(
        @NotBlank(message = "Vehicle number is required") @Size(max = 15, message = "Vehicle number is too long") String vehicleNumber,
        @Min(value = 0, message = "Floor cannot be negative") @Max(value = 9, message = "Floor must be at most 9") Integer preferredFloor,
        @Size(max = 300, message = "Note must be at most 300 characters") String note) {
}
