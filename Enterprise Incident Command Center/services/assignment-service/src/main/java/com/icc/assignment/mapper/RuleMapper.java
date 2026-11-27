package com.icc.assignment.mapper;

import com.icc.assignment.dto.RuleResponse;
import com.icc.assignment.entity.AssignmentRule;
import org.springframework.stereotype.Component;

@Component
public class RuleMapper {

    public RuleResponse toResponse(AssignmentRule rule) {
        return new RuleResponse(rule.getId(), rule.getCategory(), rule.getTeamId(), rule.isActive());
    }
}
