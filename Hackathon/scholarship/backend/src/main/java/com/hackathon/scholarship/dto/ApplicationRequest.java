package com.hackathon.scholarship.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The academic and financial details the admin needs to judge eligibility. All of them are mandatory. */
public record ApplicationRequest(
        @NotNull(message = "Choose a scholarship") Long programId,
        @NotNull(message = "CGPA is required")
        @DecimalMin(value = "0.00", message = "CGPA cannot be negative")
        @DecimalMax(value = "10.00", message = "CGPA cannot be above 10")
        @Digits(integer = 2, fraction = 2, message = "CGPA can have at most 2 decimal places") BigDecimal gpa,
        @NotNull(message = "Annual family income is required")
        @DecimalMin(value = "0.00", message = "Income cannot be negative")
        @Digits(integer = 10, fraction = 2, message = "Income can have at most 2 decimal places") BigDecimal annualFamilyIncome,
        @NotBlank(message = "Course of study is required") @Size(max = 100, message = "Course must be at most 100 characters") String course,
        @NotNull(message = "Year of study is required")
        @Min(value = 1, message = "Year of study must be between 1 and 6")
        @Max(value = 6, message = "Year of study must be between 1 and 6") Integer yearOfStudy,
        @NotBlank(message = "Please explain why you are applying")
        @Size(max = 1000, message = "Statement must be at most 1000 characters") String statement) {
}
