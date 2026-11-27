package com.hackathon.scholarship.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ProgramRequest(
        @NotBlank(message = "Name is required") @Size(max = 120, message = "Name must be at most 120 characters") String name,
        @Size(max = 500, message = "Description must be at most 500 characters") String description,
        @NotNull(message = "Award amount is required")
        @DecimalMin(value = "1.00", message = "Award amount must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Award amount can have at most 2 decimal places") BigDecimal amount,
        @NotNull(message = "Deadline is required") LocalDate deadline,
        @NotNull(message = "Minimum CGPA is required")
        @DecimalMin(value = "0.00", message = "Minimum CGPA cannot be negative")
        @DecimalMax(value = "10.00", message = "Minimum CGPA cannot be above 10")
        @Digits(integer = 2, fraction = 2, message = "CGPA can have at most 2 decimal places") BigDecimal minGpa,
        @NotNull(message = "Maximum family income is required")
        @DecimalMin(value = "1.00", message = "Maximum family income must be greater than zero")
        @Digits(integer = 10, fraction = 2, message = "Income can have at most 2 decimal places") BigDecimal maxFamilyIncome,
        Boolean active) {
}
