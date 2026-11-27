package com.icc.majorincident.controller;

import com.icc.common.audit.AuditEventResponse;
import com.icc.common.audit.AuditService;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.page.PageResponse;
import com.icc.majorincident.dto.AddTimelineEntryRequest;
import com.icc.majorincident.dto.ChangeStatusRequest;
import com.icc.majorincident.dto.DeclareMajorIncidentRequest;
import com.icc.majorincident.dto.MajorIncidentDetailResponse;
import com.icc.majorincident.dto.MajorIncidentResponse;
import com.icc.majorincident.dto.TimelineEntryResponse;
import com.icc.majorincident.service.MajorIncidentService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/major-incidents")
@PreAuthorize("hasAnyRole('SUPPORT_ENGINEER', 'ADMIN')")
public class MajorIncidentController {

    private final MajorIncidentService service;
    private final AuditService auditService;

    public MajorIncidentController(MajorIncidentService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MajorIncidentResponse declare(@Valid @RequestBody DeclareMajorIncidentRequest request,
                                         @AuthenticationPrincipal JwtClaims caller) {
        return service.declare(request, caller);
    }

    @GetMapping
    public PageResponse<MajorIncidentResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }

    // The literal path wins over /{id}.
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditEventResponse> audit(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.list(page, size);
    }

    @GetMapping("/{id}")
    public MajorIncidentDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/timeline")
    @ResponseStatus(HttpStatus.CREATED)
    public TimelineEntryResponse addTimelineEntry(@PathVariable Long id,
                                                  @Valid @RequestBody AddTimelineEntryRequest request,
                                                  @AuthenticationPrincipal JwtClaims caller) {
        return service.addTimelineEntry(id, request.text(), caller);
    }

    @PatchMapping("/{id}/status")
    public MajorIncidentResponse changeStatus(@PathVariable Long id,
                                              @Valid @RequestBody ChangeStatusRequest request,
                                              @AuthenticationPrincipal JwtClaims caller) {
        return service.changeStatus(id, request.status(), caller);
    }
}
