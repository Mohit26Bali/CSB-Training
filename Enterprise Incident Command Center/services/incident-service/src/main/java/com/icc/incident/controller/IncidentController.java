package com.icc.incident.controller;

import com.icc.common.audit.AuditEventResponse;
import com.icc.common.audit.AuditService;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.page.PageResponse;
import com.icc.incident.dto.AddCommentRequest;
import com.icc.incident.dto.CommentResponse;
import com.icc.incident.dto.CreateIncidentRequest;
import com.icc.incident.dto.HoldRequest;
import com.icc.incident.dto.IncidentDetailResponse;
import com.icc.incident.dto.IncidentResponse;
import com.icc.incident.dto.ReassignRequest;
import com.icc.incident.dto.ReopenRequest;
import com.icc.incident.dto.ResolveRequest;
import com.icc.incident.dto.UpdateIncidentRequest;
import com.icc.incident.service.IncidentService;
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
@RequestMapping("/api/incidents")
public class IncidentController {

    private static final String ENGINEER = "hasRole('SUPPORT_ENGINEER')";
    private static final String ENGINEER_OR_ADMIN = "hasAnyRole('SUPPORT_ENGINEER', 'ADMIN')";

    private final IncidentService incidentService;
    private final AuditService auditService;

    public IncidentController(IncidentService incidentService, AuditService auditService) {
        this.incidentService = incidentService;
        this.auditService = auditService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IncidentResponse create(@Valid @RequestBody CreateIncidentRequest request,
                                   @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.create(request, caller);
    }

    @GetMapping
    public PageResponse<IncidentResponse> list(
            @RequestParam(defaultValue = "mine") String scope,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.list(scope, state, q, page, size, caller);
    }

    // Declared before /{id}: the literal path wins, so "audit" is never read as an id.
    @GetMapping("/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditEventResponse> audit(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.list(page, size);
    }

    @GetMapping("/{id}")
    public IncidentDetailResponse get(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.get(id, caller);
    }

    @PatchMapping("/{id}")
    @PreAuthorize(ENGINEER_OR_ADMIN)
    public IncidentResponse update(@PathVariable Long id, @Valid @RequestBody UpdateIncidentRequest request,
                                   @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.update(id, request, caller);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentResponse addComment(@PathVariable Long id, @Valid @RequestBody AddCommentRequest request,
                                      @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.addComment(id, request, caller);
    }

    @PostMapping("/{id}/accept")
    @PreAuthorize(ENGINEER)
    public IncidentResponse accept(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.accept(id, caller);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(ENGINEER)
    public IncidentResponse resolve(@PathVariable Long id, @Valid @RequestBody ResolveRequest request,
                                    @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.resolve(id, request, caller);
    }

    @PostMapping("/{id}/close")
    @PreAuthorize(ENGINEER)
    public IncidentResponse close(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.close(id, caller);
    }

    @PostMapping("/{id}/hold")
    @PreAuthorize(ENGINEER)
    public IncidentResponse hold(@PathVariable Long id, @Valid @RequestBody HoldRequest request,
                                 @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.hold(id, request, caller);
    }

    @PostMapping("/{id}/resume")
    @PreAuthorize(ENGINEER)
    public IncidentResponse resume(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.resume(id, caller);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(ENGINEER)
    public IncidentResponse cancel(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.cancel(id, caller);
    }

    // The reporter (an employee) or an engineer of the owning team. The service checks which one.
    @PostMapping("/{id}/reopen")
    @PreAuthorize("hasAnyRole('EMPLOYEE', 'SUPPORT_ENGINEER')")
    public IncidentResponse reopen(@PathVariable Long id, @Valid @RequestBody ReopenRequest request,
                                   @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.reopen(id, request, caller);
    }

    @PostMapping("/{id}/reassign")
    @PreAuthorize(ENGINEER)
    public IncidentResponse reassign(@PathVariable Long id, @Valid @RequestBody ReassignRequest request,
                                     @AuthenticationPrincipal JwtClaims caller) {
        return incidentService.reassign(id, request.teamId(), caller);
    }
}
