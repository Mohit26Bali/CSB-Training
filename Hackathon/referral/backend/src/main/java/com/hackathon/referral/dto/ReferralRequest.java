package com.hackathon.referral.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReferralRequest(
        @NotNull(message = "Choose a job") Long jobId,
        @NotBlank(message = "Candidate name is required") @Size(max = 100, message = "Name must be at most 100 characters") String candidateName,
        @NotBlank(message = "Candidate email is required") @Email(message = "Email is not valid") @Size(max = 150, message = "Email must be at most 150 characters") String candidateEmail,
        @Pattern(regexp = "|[0-9+()\\- ]{7,20}", message = "Phone number is not valid") String candidatePhone,
        @Pattern(regexp = "|https?://\\S{3,290}", message = "Resume link must start with http:// or https://") String resumeUrl,
        @Size(max = 500, message = "Notes must be at most 500 characters") String notes) {
}
