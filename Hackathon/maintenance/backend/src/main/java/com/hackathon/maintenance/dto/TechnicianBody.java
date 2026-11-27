package com.hackathon.maintenance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record TechnicianBody(
        @NotBlank(message = "Name is required") @Size(max = 80, message = "Name must be at most 80 characters") String name,
        @NotBlank(message = "Choose a trade") String trade,
        @Pattern(regexp = "^$|^[+0-9 ()-]{7,20}$", message = "Enter a valid phone number") String phone) {
}
