package com.icc.majorincident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The commander is optional: when it is missing, the person who declares becomes the commander. */
public record DeclareMajorIncidentRequest(
        @NotNull Long incidentId,
        @NotBlank @Size(max = 255) String summary,
        @NotBlank @Size(max = 4000) String impactSummary,
        Long commanderId) {
}
