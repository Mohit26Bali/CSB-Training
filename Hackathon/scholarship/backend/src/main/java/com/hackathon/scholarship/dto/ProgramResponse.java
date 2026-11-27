package com.hackathon.scholarship.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.hackathon.scholarship.entity.ScholarshipProgram;

/** applicationCount and approvedCount are only filled in for admins (0 for students). */
public record ProgramResponse(Long id, String name, String description, BigDecimal amount, LocalDate deadline,
                              BigDecimal minGpa, BigDecimal maxFamilyIncome, boolean active, boolean open,
                              long applicationCount, long approvedCount) {

    public static ProgramResponse from(ScholarshipProgram p, LocalDate today, long applications, long approved) {
        return new ProgramResponse(p.getId(), p.getName(), p.getDescription(), p.getAmount(), p.getDeadline(),
                p.getMinGpa(), p.getMaxFamilyIncome(), p.isActive(), p.isOpen(today), applications, approved);
    }
}
