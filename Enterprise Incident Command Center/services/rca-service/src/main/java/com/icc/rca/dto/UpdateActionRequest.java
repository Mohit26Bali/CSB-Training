package com.icc.rca.dto;

import com.icc.rca.entity.ActionStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateActionRequest(@NotNull ActionStatus status) {
}
