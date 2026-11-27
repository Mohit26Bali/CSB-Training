package com.hackathon.asset.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AssignBody(
        @NotBlank(message = "Choose an employee") String employeeUsername,
        LocalDate dueDate,
        @Size(max = 300, message = "Note must be at most 300 characters") String note) {
}
