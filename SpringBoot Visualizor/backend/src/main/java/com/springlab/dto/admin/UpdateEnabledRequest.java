package com.springlab.dto.admin;

import jakarta.validation.constraints.NotNull;

public record UpdateEnabledRequest(@NotNull Boolean enabled) {
}
