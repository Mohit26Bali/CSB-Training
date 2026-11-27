package com.icc.majorincident.controller;

import com.icc.majorincident.dto.InternalMajorIncidentResponse;
import com.icc.majorincident.dto.MajorIncidentStatsResponse;
import com.icc.majorincident.service.MajorIncidentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Service-to-service endpoints. The gateway has no route for /internal. */
@RestController
@RequestMapping("/internal/major-incidents")
public class InternalMajorIncidentController {

    private final MajorIncidentService service;

    public InternalMajorIncidentController(MajorIncidentService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public MajorIncidentStatsResponse stats() {
        return service.stats();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPPORT_ENGINEER', 'ADMIN')")
    public InternalMajorIncidentResponse get(@PathVariable Long id) {
        return service.getInternal(id);
    }
}
