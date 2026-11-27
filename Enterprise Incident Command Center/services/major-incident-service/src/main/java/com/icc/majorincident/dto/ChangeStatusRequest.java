package com.icc.majorincident.dto;

import com.icc.majorincident.entity.MajorIncidentStatus;
import jakarta.validation.constraints.NotNull;

public record ChangeStatusRequest(@NotNull MajorIncidentStatus status) {
}
