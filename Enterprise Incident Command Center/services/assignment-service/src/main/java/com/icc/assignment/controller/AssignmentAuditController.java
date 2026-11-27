package com.icc.assignment.controller;

import com.icc.common.audit.AuditEventResponse;
import com.icc.common.audit.AuditService;
import com.icc.common.page.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AssignmentAuditController {

    private final AuditService auditService;

    public AssignmentAuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/assignment/audit")
    @PreAuthorize("hasRole('ADMIN')")
    public PageResponse<AuditEventResponse> audit(@RequestParam(defaultValue = "0") @Min(0) int page,
                                                  @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return auditService.list(page, size);
    }
}
