package com.icc.incident.controller;

import com.icc.common.jwt.JwtClaims;
import com.icc.incident.dto.IncidentStatsResponse;
import com.icc.incident.dto.InternalIncidentResponse;
import com.icc.incident.dto.MajorIncidentLinkRequest;
import com.icc.incident.dto.ProblemClosedRequest;
import com.icc.incident.service.IncidentService;
import com.icc.incident.service.IncidentStatsService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints. The gateway has no route for /internal. */
@RestController
@RequestMapping("/internal/incidents")
public class InternalIncidentController {

    private static final String ENGINEER_OR_ADMIN = "hasAnyRole('SUPPORT_ENGINEER', 'ADMIN')";

    private final IncidentService incidentService;
    private final IncidentStatsService statsService;

    public InternalIncidentController(IncidentService incidentService, IncidentStatsService statsService) {
        this.incidentService = incidentService;
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public IncidentStatsResponse stats(@RequestParam(defaultValue = "7") @Min(1) @Max(90) int days) {
        return statsService.stats(days);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ENGINEER_OR_ADMIN)
    public InternalIncidentResponse get(@PathVariable Long id) {
        return incidentService.getInternal(id);
    }

    /** The RCA service calls this for each incident of a problem it has just closed (D32). */
    @PostMapping("/{id}/problem-closed")
    @PreAuthorize(ENGINEER_OR_ADMIN)
    public InternalIncidentResponse problemClosed(@PathVariable Long id, @Valid @RequestBody ProblemClosedRequest request,
                                                  @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.problemClosed(id, request, caller);
    }

    @PutMapping("/{id}/major-incident-link")
    @PreAuthorize(ENGINEER_OR_ADMIN)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void linkMajorIncident(@PathVariable Long id, @Valid @RequestBody MajorIncidentLinkRequest request,
                                  @AuthenticationPrincipal JwtClaims caller) {
        incidentService.linkMajorIncident(id, request.majorIncidentId(), caller);
    }
}
