package com.icc.rca.dto;

import java.util.Map;

/** Read by the Dashboard. Open means not CLOSED. */
public record ProblemStatsResponse(long total, long open, long openActions, Map<String, Long> byStatus) {
}
