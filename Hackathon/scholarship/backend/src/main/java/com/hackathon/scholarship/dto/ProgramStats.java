package com.hackathon.scholarship.dto;

import java.math.BigDecimal;

public record ProgramStats(Long programId, String name, long total, long submitted, long approved, long rejected,
                           double averageScore, BigDecimal awardedAmount) {
}
