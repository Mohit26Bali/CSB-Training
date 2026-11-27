package com.icc.rca.dto;

import com.icc.rca.entity.ActionStatus;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ActionResponse(@NotNull Long id, @NotNull Long problemId, @NotNull String description, @NotNull Long ownerId,
                             @NotNull LocalDate dueDate, @NotNull ActionStatus status) {
}
