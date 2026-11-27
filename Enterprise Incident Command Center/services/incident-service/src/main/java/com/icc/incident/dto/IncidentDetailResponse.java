package com.icc.incident.dto;

import java.util.List;

import jakarta.validation.constraints.NotNull;

/** One incident with its comments. Employees receive only the COMMENT entries, never the work notes. */
public record IncidentDetailResponse(@NotNull IncidentResponse incident, @NotNull List<CommentResponse> comments) {
}