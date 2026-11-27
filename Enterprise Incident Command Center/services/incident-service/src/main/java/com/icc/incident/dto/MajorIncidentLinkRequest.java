package com.icc.incident.dto;

import jakarta.validation.constraints.NotNull;

public record MajorIncidentLinkRequest(@NotNull Long majorIncidentId) {
}
