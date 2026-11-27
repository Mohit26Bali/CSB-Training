package com.hackathon.hostel.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or edit a room (the admin's form). */
public record RoomRequestBody(
        @NotBlank(message = "Room number is required") @Size(max = 20, message = "Room number must be at most 20 characters")
        @Pattern(regexp = "[A-Za-z0-9 -]*", message = "Use letters, digits, spaces and dashes only") String number,
        @NotNull(message = "Floor is required") @Min(value = 0, message = "Floor cannot be negative") @Max(value = 30, message = "Floor must be at most 30") Integer floor,
        @NotBlank(message = "Choose a room type") String type,
        @NotNull(message = "Capacity is required") @Min(value = 1, message = "Capacity must be at least 1") @Max(value = 12, message = "Capacity must be at most 12") Integer capacity,
        Boolean active) {
}
