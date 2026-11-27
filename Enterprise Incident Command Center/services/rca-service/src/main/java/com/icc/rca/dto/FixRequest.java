package com.icc.rca.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The Fix step: both notes are required (as in ServiceNow). Category and workaround are optional. */
public record FixRequest(
        @NotBlank @Size(max = 4000) String causeNotes,
        @NotBlank @Size(max = 4000) String fixNotes,
        @Size(max = 60) String rootCauseCategory,
        @Size(max = 4000) String workaround) {
}
