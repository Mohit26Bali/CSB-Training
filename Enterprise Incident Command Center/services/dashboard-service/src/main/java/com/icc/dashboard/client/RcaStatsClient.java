package com.icc.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.Map;

@FeignClient(name = "rca-service")
public interface RcaStatsClient {

    @GetMapping("/internal/problems/stats")
    ProblemStats stats(@RequestHeader("Authorization") String authorization,
                       @RequestHeader("X-Correlation-Id") String correlationId);

    record ProblemStats(long total, long open, long openActions, Map<String, Long> byStatus) {
    }
}
