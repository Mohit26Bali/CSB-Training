package com.hackathon.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectBody(@NotBlank(message = "A reason is required when rejecting") @Size(max = 300, message = "Reason must be at most 300 characters") String reason) {
}
