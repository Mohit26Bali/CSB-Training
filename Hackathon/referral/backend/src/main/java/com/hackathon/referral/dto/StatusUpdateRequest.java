package com.hackathon.referral.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StatusUpdateRequest(
        @NotBlank(message = "Choose the new status") String status,
        @Size(max = 255, message = "Note must be at most 255 characters") String note) {
}
