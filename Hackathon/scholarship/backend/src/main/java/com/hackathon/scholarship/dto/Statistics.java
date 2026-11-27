package com.hackathon.scholarship.dto;

import java.math.BigDecimal;
import java.util.List;

/** approvalRate is approved / decided (0 to 1), or 0 when nothing has been decided yet. */
public record Statistics(long totalApplications, long submitted, long approved, long rejected, double approvalRate,
                         BigDecimal totalAwarded, List<ProgramStats> byProgram) {
}
