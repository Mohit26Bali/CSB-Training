package com.springlab.dto.question;

import jakarta.validation.constraints.NotNull;

public record ActiveRequest(@NotNull Boolean active) {
}
