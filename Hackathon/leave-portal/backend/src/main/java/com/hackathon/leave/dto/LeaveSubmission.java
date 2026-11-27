package com.hackathon.leave.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LeaveSubmission(
        @NotBlank(message = "Leave type is required") String leaveType,
        @NotNull(message = "Start date is required") LocalDate startDate,
        @NotNull(message = "End date is required") LocalDate endDate,
        @NotBlank(message = "Reason is required")
        @Size(max = 500, message = "Reason must be at most 500 characters") String reason) {
}
