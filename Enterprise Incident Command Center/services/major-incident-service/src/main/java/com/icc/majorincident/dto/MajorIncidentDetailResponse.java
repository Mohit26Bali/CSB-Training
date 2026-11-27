package com.icc.majorincident.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record MajorIncidentDetailResponse(@NotNull MajorIncidentResponse majorIncident, @NotNull List<TimelineEntryResponse> timeline) {
}
