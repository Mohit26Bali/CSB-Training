package com.hackathon.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** notes: mandatory when resolving; otherwise an optional remark. */
public record StatusBody(@NotBlank(message = "Choose a status") String status,
                         @Size(max = 1000, message = "Notes must be at most 1000 characters") String notes) {
}
