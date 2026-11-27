package com.hackathon.procurement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "A reason is required when rejecting a request")
        @Size(max = 255, message = "Reason must be at most 255 characters") String reason) {
}
