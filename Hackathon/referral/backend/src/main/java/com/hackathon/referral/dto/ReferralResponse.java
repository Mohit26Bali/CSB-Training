package com.hackathon.referral.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.hackathon.referral.entity.Referral;

/** allowedNext lists the statuses a recruiter may move this referral to (empty once it is final). */
public record ReferralResponse(Long id, Long jobId, String jobTitle, String jobDepartment, String referrerUsername,
                               String referrerName, String candidateName, String candidateEmail,
                               String candidatePhone, String resumeUrl, String notes, String status,
                               List<String> allowedNext, LocalDateTime submittedAt, String updatedBy,
                               LocalDateTime updatedAt, String statusNote) {

    public static ReferralResponse from(Referral r) {
        return new ReferralResponse(r.getId(), r.getJob().getId(), r.getJob().getTitle(), r.getJob().getDepartment(),
                r.getReferrer().getUsername(), r.getReferrer().getFullname(), r.getCandidateName(),
                r.getCandidateEmail(), r.getCandidatePhone(), r.getResumeUrl(), r.getNotes(), r.getStatus().name(),
                r.getStatus().next().stream().sorted().map(Enum::name).toList(), r.getSubmittedAt(), r.getUpdatedBy(),
                r.getUpdatedAt(), r.getStatusNote());
    }
}
