package com.cartnest.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 100, message = "Full name must be at most 100 characters") String fullName,
        @NotBlank(message = "Email is required")
        @Email(message = "Enter a valid email address")
        @Size(max = 150, message = "Email must be at most 150 characters") String email,
        @NotBlank(message = "Password is required")
        @Pattern(regexp = PasswordRules.PATTERN, message = PasswordRules.MESSAGE) String password,
        @Pattern(regexp = "[6-9][0-9]{9}", message = "Enter a 10-digit Indian mobile number") String phone) {
}
