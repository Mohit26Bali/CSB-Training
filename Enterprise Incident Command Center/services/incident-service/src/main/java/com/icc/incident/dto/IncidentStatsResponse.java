package com.icc.incident.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Read by the Dashboard service. Open means NEW, IN_PROGRESS or ON_HOLD. */
public record IncidentStatsResponse(
        long total,
        long open,
        long criticalOpen,
        long resolvedToday,
        Map<String, Long> byState,
        Map<Integer, Long> byPriority,
        List<DailyCount> daily) {

    public record DailyCount(LocalDate date, long created, long resolved) {
    }
}
