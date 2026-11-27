package com.icc.assignment.controller;

import com.icc.assignment.dto.RuleResponse;
import com.icc.assignment.dto.UpdateRuleRequest;
import com.icc.assignment.service.AssignmentService;
import com.icc.common.jwt.JwtClaims;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/assignment-rules")
@PreAuthorize("hasRole('ADMIN')")
public class AssignmentRuleController {

    private final AssignmentService assignmentService;

    public AssignmentRuleController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    @GetMapping
    public List<RuleResponse> list() {
        return assignmentService.listRules();
    }

    @PutMapping("/{id}")
    public RuleResponse update(@PathVariable Long id, @Valid @RequestBody UpdateRuleRequest request,
                               @AuthenticationPrincipal JwtClaims claims) {
        return assignmentService.updateRule(id, request.teamId(), claims);
    }
}
