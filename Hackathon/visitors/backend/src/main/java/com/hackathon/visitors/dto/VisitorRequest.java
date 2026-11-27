package com.hackathon.visitors.dto;

import java.time.LocalDate;
import java.time.LocalTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VisitorRequest(
        @NotBlank(message = "Visitor name is required") @Size(max = 100, message = "Name must be at most 100 characters") String visitorName,
        @NotBlank(message = "Visitor phone is required") @Pattern(regexp = "^$|^[+]?[0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone,
        @Size(max = 100, message = "Company must be at most 100 characters") String company,
        @NotBlank(message = "Purpose of the visit is required") @Size(max = 200, message = "Purpose must be at most 200 characters") String purpose,
        @NotNull(message = "Expected date is required") LocalDate expectedDate,
        LocalTime expectedTime) {
}
