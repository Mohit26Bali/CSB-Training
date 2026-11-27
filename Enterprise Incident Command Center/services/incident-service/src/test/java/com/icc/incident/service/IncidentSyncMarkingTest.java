package com.icc.incident.service;

import com.icc.common.audit.AuditService;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.incident.client.AssignmentClient;
import com.icc.incident.client.AssignmentClient.ResolveResponse;
import com.icc.incident.client.AssignmentClient.TeamDto;
import com.icc.incident.dto.AddCommentRequest;
import com.icc.incident.dto.CreateIncidentRequest;
import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.entity.SyncStatus;
import com.icc.incident.itsm.ItsmSettings;
import com.icc.incident.mapper.IncidentMapper;
import com.icc.incident.repository.IncidentCommentRepository;
import com.icc.incident.repository.IncidentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Which changes of an incident make it wait for ServiceNow, and what is copied for the sync (D38). */
@ExtendWith(MockitoExtension.class)
class IncidentSyncMarkingTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentCommentRepository commentRepository;
    @Mock
    private AssignmentClient assignmentClient;
    @Mock
    private AuditService auditService;

    private final JwtClaims reporter = new JwtClaims(6L, "ananya@icc.com", Role.EMPLOYEE, null);
    private final JwtClaims engineer = new JwtClaims(3L, "sai@icc.com", Role.SUPPORT_ENGINEER, 2L);

    private IncidentService service(String mode) {
        return new IncidentService(incidentRepository, commentRepository, assignmentClient, new PriorityCalculator(),
                new IncidentMapper(), auditService, new ItsmSettings(mode));
    }

    private Incident incident(IncidentState state) {
        Incident incident = new Incident("Switch down", "The core switch is down", "Network", 1, 1, 1, 6L, 2L);
        ReflectionTestUtils.setField(incident, "id", 5L);
        incident.setNumber("INC0000005");
        incident.setState(state);
        return incident;
    }

    private void created() {
        when(assignmentClient.resolve("Network")).thenReturn(new ResolveResponse(2L));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });
    }

    // ------------------------------------------------------------------ create

    @Test
    void withTheSyncOn_aNewIncidentWaits_andCarriesTheEmailAndTheTeamName() {
        created();
        when(assignmentClient.teams()).thenReturn(List.of(new TeamDto(1L, "Service Desk", "d"), new TeamDto(2L, "Network", "n")));
        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);

        service("servicenow").create(new CreateIncidentRequest("Switch down", "Core switch is down", "Network", null, 1, 1), reporter);

        verify(incidentRepository).save(saved.capture());
        assertEquals(SyncStatus.PENDING, saved.getValue().getSyncStatus());
        assertEquals("ananya@icc.com", saved.getValue().getReporterEmail());
        assertEquals("Network", saved.getValue().getAssignedTeamName());
    }

    @Test
    void withTheSyncOff_nothingChanges_andNoExtraCallIsMade() {
        created();
        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);

        service("local").create(new CreateIncidentRequest("Switch down", "Core switch is down", "Network", null, 1, 1), reporter);

        verify(incidentRepository).save(saved.capture());
        assertEquals(SyncStatus.LOCAL_ONLY, saved.getValue().getSyncStatus());
        assertEquals("ananya@icc.com", saved.getValue().getReporterEmail());
        assertNull(saved.getValue().getAssignedTeamName());
        verify(assignmentClient, never()).teams();
    }

    @Test
    void aFailedTeamLookup_doesNotStopTheReportingOfAnIncident() {
        created();
        when(assignmentClient.teams()).thenThrow(new IllegalStateException("assignment is slow"));
        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);

        service("servicenow").create(new CreateIncidentRequest("Switch down", "Core switch is down", "Network", null, 1, 1), reporter);

        verify(incidentRepository).save(saved.capture());
        assertNull(saved.getValue().getAssignedTeamName());
        assertEquals(SyncStatus.PENDING, saved.getValue().getSyncStatus());
    }

    // ------------------------------------------------------------------ later changes

    @Test
    void accepting_copiesTheEngineersEmail_andWaitsForTheSync() {
        Incident incident = incident(IncidentState.NEW);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(incident));

        service("servicenow").accept(5L, engineer);

        assertEquals("sai@icc.com", incident.getAssignedToEmail());
        assertEquals(SyncStatus.PENDING, incident.getSyncStatus());
    }

    @Test
    void accepting_withTheSyncOff_leavesTheIncidentLocalOnly() {
        Incident incident = incident(IncidentState.NEW);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(incident));

        service("local").accept(5L, engineer);

        assertEquals(SyncStatus.LOCAL_ONLY, incident.getSyncStatus());
    }

    @Test
    void reassigning_copiesTheNewTeamName_andClearsTheAssignee() {
        Incident incident = incident(IncidentState.IN_PROGRESS);
        incident.setAssignedToId(3L);
        incident.setAssignedToEmail("sai@icc.com");
        incident.setAssignedTeamName("Network");
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(incident));
        when(assignmentClient.teams()).thenReturn(List.of(new TeamDto(2L, "Network", "n"), new TeamDto(3L, "Database", "d")));

        service("servicenow").reassign(5L, 3L, engineer);

        assertEquals("Database", incident.getAssignedTeamName());
        assertNull(incident.getAssignedToEmail());
        assertEquals(SyncStatus.PENDING, incident.getSyncStatus());
    }

    @Test
    void aComment_waitsForTheSync_onlyWhenItIsOn() {
        Incident incident = incident(IncidentState.IN_PROGRESS);
        when(incidentRepository.findById(5L)).thenReturn(Optional.of(incident));
        when(commentRepository.save(any(IncidentComment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ArgumentCaptor<IncidentComment> saved = ArgumentCaptor.forClass(IncidentComment.class);

        service("servicenow").addComment(5L, new AddCommentRequest(CommentType.WORK_NOTE, "Replacing the cable"), engineer);
        service("local").addComment(5L, new AddCommentRequest(CommentType.WORK_NOTE, "Second note"), engineer);

        verify(commentRepository, org.mockito.Mockito.times(2)).save(saved.capture());
        assertEquals(SyncStatus.PENDING, saved.getAllValues().get(0).getSyncStatus());
        assertEquals(SyncStatus.LOCAL_ONLY, saved.getAllValues().get(1).getSyncStatus());
    }
}
