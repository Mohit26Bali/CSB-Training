package com.icc.dashboard.controller;

import com.icc.common.correlation.CorrelationIds;
import com.icc.dashboard.dto.DashboardSummaryResponse;
import com.icc.dashboard.dto.TrendsResponse;
import com.icc.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("hasRole('ADMIN')")
public class DashboardController {

    private final DashboardService service;

    public DashboardController(DashboardService service) {
        this.service = service;
    }

    // The two headers are hidden from the OpenAPI contract (D24): the frontend client adds them itself.
    // The token and the correlation id are read here, on the request thread, and handed to the service,
    // because the service makes its calls on other threads.

    @GetMapping("/summary")
    public DashboardSummaryResponse summary(@Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                            @Parameter(hidden = true)
                                            @RequestHeader(value = CorrelationIds.HEADER, required = false)
                                            String correlationId) {
        return service.summary(authorization, correlationId);
    }

    @GetMapping("/trends")
    public TrendsResponse trends(@RequestParam(defaultValue = "7") int days,
                                 @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
                                 @Parameter(hidden = true)
                                 @RequestHeader(value = CorrelationIds.HEADER, required = false)
                                 String correlationId) {
        return service.trends(days, authorization, correlationId);
    }
}
