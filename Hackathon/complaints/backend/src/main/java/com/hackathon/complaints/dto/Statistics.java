package com.hackathon.complaints.dto;

import java.util.List;

public record Statistics(long total, long open, long inProgress, long resolved, long closed, long unassigned, long mine,
                         long waitingOverThreeDays, int resolutionRatePercent, double averageResolutionHours,
                         double averageRating, long ratings, List<Count> byCategory, List<Count> activeByPriority,
                         List<DayCount> lastSevenDays) {
}
