package com.hackathon.parking.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Create or edit a slot (the admin's form). status is only used on edit; a new slot is AVAILABLE. */
public record SlotBody(
        @NotBlank(message = "Slot code is required") @Size(max = 20, message = "Slot code must be at most 20 characters")
        @Pattern(regexp = "[A-Za-z0-9 -]*", message = "Use letters, digits, spaces and dashes only") String code,
        @NotNull(message = "Floor is required") @Min(value = 0, message = "Floor cannot be negative") @Max(value = 9, message = "Floor must be at most 9") Integer floor,
        String status) {
}
