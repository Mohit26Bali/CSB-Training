package com.icc.rca.dto;

import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddActionRequest(
        @NotBlank @Size(max = 4000) String description,
        @NotNull Long ownerId,
        @NotNull @FutureOrPresent LocalDate dueDate) {
}
