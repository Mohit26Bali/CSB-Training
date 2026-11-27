package com.icc.assignment.service;

import com.icc.assignment.dto.ResolveResponse;
import com.icc.assignment.dto.RuleResponse;
import com.icc.assignment.entity.AssignmentRule;
import com.icc.assignment.mapper.RuleMapper;
import com.icc.assignment.repository.AssignmentRuleRepository;
import com.icc.assignment.repository.TeamRepository;
import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** The routing rules: which team handles which incident category. */
@Service
public class AssignmentService {

    private final AssignmentRuleRepository ruleRepository;
    private final TeamRepository teamRepository;
    private final RuleMapper ruleMapper;
    private final AuditService auditService;

    public AssignmentService(AssignmentRuleRepository ruleRepository, TeamRepository teamRepository,
                             RuleMapper ruleMapper, AuditService auditService) {
        this.ruleRepository = ruleRepository;
        this.teamRepository = teamRepository;
        this.ruleMapper = ruleMapper;
        this.auditService = auditService;
    }

    /** Called by the Incident service when an incident is reported. */
    public ResolveResponse resolveTeam(String category) {
        AssignmentRule rule = ruleRepository.findByCategoryAndActiveTrue(category)
                .orElseThrow(() -> new NotFoundException("No assignment rule for category " + category));
        return new ResolveResponse(rule.getTeamId());
    }

    public List<RuleResponse> listRules() {
        return ruleRepository.findAll(Sort.by("id")).stream()
                .map(ruleMapper::toResponse)
                .toList();
    }

    @Transactional
    public RuleResponse updateRule(Long ruleId, Long teamId, JwtClaims actor) {
        AssignmentRule rule = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new NotFoundException("Assignment rule", ruleId));
        if (!teamRepository.existsById(teamId)) {
            throw new BadRequestException("Team " + teamId + " does not exist");
        }

        Long oldTeamId = rule.getTeamId();
        rule.changeTeam(teamId);

        auditService.record(actor.userId(), "RULE_CHANGED", "ASSIGNMENT_RULE", rule.getId(),
                "Category " + rule.getCategory() + ": team " + oldTeamId + " to team " + teamId);
        return ruleMapper.toResponse(rule);
    }
}
