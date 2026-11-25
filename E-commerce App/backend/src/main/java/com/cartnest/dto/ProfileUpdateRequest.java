package com.cartnest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** The email cannot be changed: it identifies the account. */
public record ProfileUpdateRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters") String fullName,
        @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter a 10-digit Indian mobile number") String phone) {
}
