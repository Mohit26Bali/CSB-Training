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
import com.icc.common.jwt.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AssignmentServiceTest {

    @Mock
    private AssignmentRuleRepository ruleRepository;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private AuditService auditService;

    private AssignmentService service;
    private final JwtClaims admin = new JwtClaims(1L, "admin", Role.ADMIN, null);

    @BeforeEach
    void setUp() {
        service = new AssignmentService(ruleRepository, teamRepository, new RuleMapper(), auditService);
    }

    @Test
    void resolve_returnsTheTeamOfTheActiveRule() {
        when(ruleRepository.findByCategoryAndActiveTrue("Network"))
                .thenReturn(Optional.of(new AssignmentRule(3L, "Network", 2L, true)));

        ResolveResponse response = service.resolveTeam("Network");

        assertEquals(2L, response.teamId());
    }

    @Test
    void resolve_withoutARule_isNotFound() {
        when(ruleRepository.findByCategoryAndActiveTrue("Printing")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.resolveTeam("Printing"));
    }

    @Test
    void updateRule_changesTheTeam_andAudits() {
        AssignmentRule rule = new AssignmentRule(3L, "Network", 2L, true);
        when(ruleRepository.findById(3L)).thenReturn(Optional.of(rule));
        when(teamRepository.existsById(4L)).thenReturn(true);

        RuleResponse response = service.updateRule(3L, 4L, admin);

        assertEquals(4L, response.teamId());
        assertEquals(4L, rule.getTeamId());
        verify(auditService).record(eq(1L), eq("RULE_CHANGED"), eq("ASSIGNMENT_RULE"), eq(3L),
                contains("team 2 to team 4"));
    }

    @Test
    void updateRule_withUnknownRule_isNotFound() {
        when(ruleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.updateRule(99L, 4L, admin));
    }

    @Test
    void updateRule_withUnknownTeam_isRejected_andNotAudited() {
        when(ruleRepository.findById(3L)).thenReturn(Optional.of(new AssignmentRule(3L, "Network", 2L, true)));
        when(teamRepository.existsById(77L)).thenReturn(false);

        assertThrows(BadRequestException.class, () -> service.updateRule(3L, 77L, admin));

        verify(auditService, never()).record(any(), anyString(), anyString(), anyLong(), anyString());
    }
}
