package com.icc.rca.controller;

import com.icc.common.audit.AuditEventResponse;
import com.icc.common.audit.AuditService;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.page.PageResponse;
import com.icc.rca.dto.AcceptRiskRequest;
import com.icc.rca.dto.ActionResponse;
import com.icc.rca.dto.AddActionRequest;
import com.icc.rca.dto.AssessRequest;
import com.icc.rca.dto.CreateProblemRequest;
import com.icc.rca.dto.FixRequest;
import com.icc.rca.dto.MarkDuplicateRequest;
import com.icc.rca.dto.ProblemDetailResponse;
import com.icc.rca.dto.ProblemResponse;
import com.icc.rca.dto.UpdateActionRequest;
import com.icc.rca.dto.UpdateProblemRequest;
import com.icc.rca.service.ProblemService;
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
@RequestMapping("/api/problems")
public class ProblemController {

    private static final String ENGINEER_OR_ADMIN = "hasAnyRole('SUPPORT_ENGINEER', 'ADMIN')";
    private static final String ADMIN = "hasRole('ADMIN')";

    private final ProblemService service;
    private final AuditService auditService;

    public ProblemController(ProblemService service, AuditService auditService) {
        this.service = service;
        this.auditService = auditService;
    }

    @PostMapping
    @PreAuthorize(ENGINEER_OR_ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public ProblemResponse create(@Valid @RequestBody CreateProblemRequest request,
                                  @AuthenticationPrincipal JwtClaims caller) {
        return service.create(request, caller);
    }

    @GetMapping
    @PreAuthorize(ENGINEER_OR_ADMIN)
    public PageResponse<ProblemResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.list(status, page, size);
    }

    // The literal path wins over /{id}.
    @GetMapping("/audit")
    @PreAuthorize(ADMIN)
    public PageResponse<AuditEventResponse> audit(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.list(page, size);
    }

    @GetMapping("/{id}")
    @PreAuthorize(ENGINEER_OR_ADMIN)
    public ProblemDetailResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    /** Edits the form fields (impact, urgency, assignee, notes). The steps themselves are the actions below. */
    @PatchMapping("/{id}")
    @PreAuthorize(ADMIN)
    public ProblemResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProblemRequest request,
                                  @AuthenticationPrincipal JwtClaims caller) {
        return service.update(id, request, caller);
    }

    @PostMapping("/{id}/actions")
    @PreAuthorize(ADMIN)
    @ResponseStatus(HttpStatus.CREATED)
    public ActionResponse addAction(@PathVariable Long id, @Valid @RequestBody AddActionRequest request,
                                    @AuthenticationPrincipal JwtClaims caller) {
        return service.addAction(id, request, caller);
    }

    @PatchMapping("/{id}/actions/{actionId}")
    @PreAuthorize(ADMIN)
    public ActionResponse updateAction(@PathVariable Long id, @PathVariable Long actionId,
                                       @Valid @RequestBody UpdateActionRequest request,
                                       @AuthenticationPrincipal JwtClaims caller) {
        return service.updateAction(id, actionId, request.status(), caller);
    }

    // The steps of a problem: one URL per button (D31). Which one is allowed now is decided in the service.

    @PostMapping("/{id}/assess")
    @PreAuthorize(ADMIN)
    public ProblemResponse assess(@PathVariable Long id, @Valid @RequestBody(required = false) AssessRequest request,
                                  @AuthenticationPrincipal JwtClaims caller) {
        return service.assess(id, request, caller);
    }

    @PostMapping("/{id}/confirm")
    @PreAuthorize(ADMIN)
    public ProblemResponse confirm(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return service.confirm(id, caller);
    }

    @PostMapping("/{id}/fix")
    @PreAuthorize(ADMIN)
    public ProblemResponse fix(@PathVariable Long id, @Valid @RequestBody FixRequest request,
                               @AuthenticationPrincipal JwtClaims caller) {
        return service.fix(id, request, caller);
    }

    @PostMapping("/{id}/resolve")
    @PreAuthorize(ADMIN)
    public ProblemResponse resolve(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return service.resolve(id, caller);
    }

    @PostMapping("/{id}/complete")
    @PreAuthorize(ADMIN)
    public ProblemResponse complete(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return service.complete(id, caller);
    }

    @PostMapping("/{id}/accept-risk")
    @PreAuthorize(ADMIN)
    public ProblemResponse acceptRisk(@PathVariable Long id,
                                      @Valid @RequestBody(required = false) AcceptRiskRequest request,
                                      @AuthenticationPrincipal JwtClaims caller) {
        return service.acceptRisk(id, request, caller);
    }

    @PostMapping("/{id}/mark-duplicate")
    @PreAuthorize(ADMIN)
    public ProblemResponse markDuplicate(@PathVariable Long id, @Valid @RequestBody MarkDuplicateRequest request,
                                         @AuthenticationPrincipal JwtClaims caller) {
        return service.markDuplicate(id, request, caller);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize(ADMIN)
    public ProblemResponse cancel(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return service.cancel(id, caller);
    }

    @PostMapping("/{id}/re-analyze")
    @PreAuthorize(ADMIN)
    public ProblemResponse reAnalyze(@PathVariable Long id, @AuthenticationPrincipal JwtClaims caller) {
        return service.reAnalyze(id, caller);
    }
}
