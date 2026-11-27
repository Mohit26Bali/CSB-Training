package com.icc.rca.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * The incidents the problem covers, and optionally the major incident it came from.
 * Impact and urgency are 1 High to 3 Low and default to 3. A problem created with an assignee starts at ASSESS.
 */
public record CreateProblemRequest(
        @NotBlank @Size(max = 200) String title,
        @NotBlank @Size(max = 4000) String description,
        @NotEmpty List<@NotNull Long> incidentIds,
        Long majorIncidentId,
        @Min(1) @Max(3) Integer impact,
        @Min(1) @Max(3) Integer urgency,
        Long assignedToId) {
}
