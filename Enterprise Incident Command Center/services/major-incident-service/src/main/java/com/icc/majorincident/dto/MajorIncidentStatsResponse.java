package com.icc.majorincident.dto;

import java.util.Map;

/** Read by the Dashboard. Active means DECLARED or MITIGATING. */
public record MajorIncidentStatsResponse(long total, long active, Map<String, Long> byStatus) {
}
