package com.icc.rca.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/** Edits to the form fields. Only the fields that are sent change. Not allowed once the problem is CLOSED. */
public record UpdateProblemRequest(
        @Min(1) @Max(3) Integer impact,
        @Min(1) @Max(3) Integer urgency,
        Long assignedToId,
        @Size(max = 4000) String causeNotes,
        @Size(max = 4000) String fixNotes,
        @Size(max = 60) String rootCauseCategory,
        @Size(max = 4000) String workaround) {
}
