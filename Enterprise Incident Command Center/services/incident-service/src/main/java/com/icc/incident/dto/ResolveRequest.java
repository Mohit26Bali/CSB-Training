package com.icc.incident.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResolveRequest(@NotBlank @Size(max = 60) String resolutionCode,
                             @NotBlank @Size(max = 4000) String resolutionNotes) {
}
