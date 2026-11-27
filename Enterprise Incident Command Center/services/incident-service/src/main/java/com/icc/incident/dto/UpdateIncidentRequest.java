package com.icc.incident.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Every field is optional. Only the fields that are sent are changed. An empty subcategory clears it. */
public record UpdateIncidentRequest(
        String category,
        @Size(max = 50) String subcategory,
        @Min(1) @Max(3) Integer impact,
        @Min(1) @Max(3) Integer urgency,
        @Size(min = 1, max = 4000) String description) {
}
