package com.icc.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The token and correlation id are passed as explicit parameters: the Dashboard calls its three
 * services on other threads, where the request (and so the automatic forwarding) is not available.
 */
@FeignClient(name = "incident-service")
public interface IncidentStatsClient {

    @GetMapping("/internal/incidents/stats")
    IncidentStats stats(@RequestParam("days") int days,
                        @RequestHeader("Authorization") String authorization,
                        @RequestHeader("X-Correlation-Id") String correlationId);

    record IncidentStats(long total, long open, long criticalOpen, long resolvedToday,
                         Map<String, Long> byState, Map<Integer, Long> byPriority, List<DailyCount> daily) {
    }

    record DailyCount(LocalDate date, long created, long resolved) {
    }
}
