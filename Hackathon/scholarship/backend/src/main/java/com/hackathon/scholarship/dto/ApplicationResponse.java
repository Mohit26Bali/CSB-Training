package com.hackathon.scholarship.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.hackathon.scholarship.entity.Application;

public record ApplicationResponse(Long id, Long programId, String programName, BigDecimal programAmount,
                                  String studentUsername, String studentName, BigDecimal gpa,
                                  BigDecimal annualFamilyIncome, String course, int yearOfStudy, String statement,
                                  int eligibilityScore, boolean meetsCriteria, String status,
                                  LocalDateTime submittedAt, String decidedBy, LocalDateTime decidedAt,
                                  String decisionReason) {

    public static ApplicationResponse from(Application a) {
        return new ApplicationResponse(a.getId(), a.getProgram().getId(), a.getProgram().getName(),
                a.getProgram().getAmount(), a.getStudent().getUsername(), a.getStudent().getFullname(), a.getGpa(),
                a.getAnnualFamilyIncome(), a.getCourse(), a.getYearOfStudy(), a.getStatement(),
                a.getEligibilityScore(), a.isMeetsCriteria(), a.getStatus().name(), a.getSubmittedAt(),
                a.getDecidedBy(), a.getDecidedAt(), a.getDecisionReason());
    }
}
