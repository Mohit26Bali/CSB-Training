package com.icc.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "major-incident-service")
public interface MajorIncidentStatsClient {

    @GetMapping("/internal/major-incidents/stats")
    MajorIncidentStats stats(@RequestHeader("Authorization") String authorization,
                             @RequestHeader("X-Correlation-Id") String correlationId);

    record MajorIncidentStats(long total, long active, Map<String, Long> byStatus) {
    }
}
