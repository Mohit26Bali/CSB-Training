package com.cartnest.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SpecInput(@NotBlank(message = "Specification name is required") @Size(max = 60) String name,
        @NotBlank(message = "Specification value is required") @Size(max = 200) String value) {
}
