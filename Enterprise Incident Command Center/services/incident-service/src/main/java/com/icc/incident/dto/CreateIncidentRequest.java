package com.icc.incident.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Impact and urgency are 1 (High) to 3 (Low). Priority is never sent: the service calculates it. */
public record CreateIncidentRequest(
        @NotBlank @Size(max = 160) String shortDescription,
        @NotBlank @Size(max = 4000) String description,
        @NotBlank String category,
        @Size(max = 50) String subcategory,
        @NotNull @Min(1) @Max(3) Integer impact,
        @NotNull @Min(1) @Max(3) Integer urgency) {
}
