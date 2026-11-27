package com.hackathon.expense.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RejectRequest(
        @NotBlank(message = "A reason is required when rejecting an expense")
        @Size(max = 255, message = "Reason must be at most 255 characters") String reason) {
}
