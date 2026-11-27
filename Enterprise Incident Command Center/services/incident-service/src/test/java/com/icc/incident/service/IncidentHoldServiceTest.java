package com.icc.incident.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.ForbiddenException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.incident.client.AssignmentClient;
import com.icc.incident.client.AssignmentClient.ResolveResponse;
import com.icc.incident.client.AssignmentClient.TeamDto;
import com.icc.incident.dto.CreateIncidentRequest;
import com.icc.incident.dto.HoldRequest;
import com.icc.incident.dto.IncidentResponse;
import com.icc.incident.dto.InternalIncidentResponse;
import com.icc.incident.dto.ProblemClosedRequest;
import com.icc.incident.dto.ResolveRequest;
import com.icc.incident.dto.UpdateIncidentRequest;
import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.HoldReason;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.itsm.ItsmSettings;
import com.icc.incident.mapper.IncidentMapper;
import com.icc.incident.repository.IncidentCommentRepository;
import com.icc.incident.repository.IncidentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The ServiceNow-style additions to the incident (D32): On Hold, Cancel, subcategory, and the problem-closed notice. */
@ExtendWith(MockitoExtension.class)
class IncidentHoldServiceTest {

    @Mock
    private IncidentRepository incidentRepository;
    @Mock
    private IncidentCommentRepository commentRepository;
    @Mock
    private AssignmentClient assignmentClient;
    @Mock
    private AuditService auditService;

    private IncidentService service;

    private final JwtClaims employee = new JwtClaims(6L, "employee1", Role.EMPLOYEE, null);
    private final JwtClaims engineer = new JwtClaims(3L, "net.engineer", Role.SUPPORT_ENGINEER, 2L);
    private final JwtClaims otherTeamEngineer = new JwtClaims(4L, "db.engineer", Role.SUPPORT_ENGINEER, 3L);
    private final JwtClaims admin = new JwtClaims(1L, "admin", Role.ADMIN, null);

    @BeforeEach
    void setUp() {
        service = new IncidentService(incidentRepository, commentRepository, assignmentClient,
                new PriorityCalculator(), new IncidentMapper(), auditService, new ItsmSettings("local"));
    }

    /** An incident reported by user 6, assigned to team 2 and (when not NEW) being worked on by engineer 3. */
    private Incident incident(Long id, IncidentState state) {
        Incident incident = new Incident("Switch down", "The core switch is down", "Network", 2, 2, 3, 6L, 2L);
        ReflectionTestUtils.setField(incident, "id", id);
        incident.setNumber(String.format("INC%07d", id));
        incident.setState(state);
        if (state != IncidentState.NEW) {
            incident.setAssignedToId(3L);
        }
        return incident;
    }

    private Incident given(Long id, IncidentState state) {
        Incident incident = incident(id, state);
        when(incidentRepository.findById(id)).thenReturn(Optional.of(incident));
        return incident;
    }

    // ------------------------------------------------------------------ hold

    @Test
    void hold_putsTheIncidentOnHold_withTheReason() {
        given(5L, IncidentState.IN_PROGRESS);

        IncidentResponse response = service.hold(5L, new HoldRequest(HoldReason.AWAITING_VENDOR, " Vendor ticket 77 "), engineer);

        assertEquals(IncidentState.ON_HOLD, response.state());
        assertEquals(HoldReason.AWAITING_VENDOR, response.holdReason());
        // For a reason other than the caller, the comment is an internal work note.
        ArgumentCaptor<IncidentComment> saved = ArgumentCaptor.forClass(IncidentComment.class);
        verify(commentRepository).save(saved.capture());
        assertEquals(CommentType.WORK_NOTE, saved.getValue().getType());
        assertEquals("Vendor ticket 77", saved.getValue().getBody());
        verify(auditService).record(eq(3L), eq("INCIDENT_HELD"), eq("INCIDENT"), eq(5L), anyString());
    }

    @Test
    void hold_withoutAComment_savesNoComment() {
        given(5L, IncidentState.IN_PROGRESS);

        service.hold(5L, new HoldRequest(HoldReason.AWAITING_CHANGE, null), engineer);

        verify(commentRepository, never()).save(any());
    }

    @Test
    void hold_awaitingTheCaller_needsAComment_theCallerCanSee() {
        Incident incident = given(5L, IncidentState.IN_PROGRESS);

        assertThrows(BadRequestException.class,
                () -> service.hold(5L, new HoldRequest(HoldReason.AWAITING_CALLER, "  "), engineer));
        assertEquals(IncidentState.IN_PROGRESS, incident.getState());

        service.hold(5L, new HoldRequest(HoldReason.AWAITING_CALLER, "Please send a screenshot"), engineer);

        ArgumentCaptor<IncidentComment> saved = ArgumentCaptor.forClass(IncidentComment.class);
        verify(commentRepository).save(saved.capture());
        assertEquals(CommentType.COMMENT, saved.getValue().getType());
    }

    @Test
    void hold_onlyFromInProgress() {
        given(5L, IncidentState.NEW);

        assertThrows(ConflictException.class,
                () -> service.hold(5L, new HoldRequest(HoldReason.AWAITING_VENDOR, null), engineer));
    }

    @Test
    void hold_onlyByTheEngineerWhoIsWorkingOnIt() {
        Incident incident = given(6L, IncidentState.IN_PROGRESS);
        incident.setAssignedToId(99L);

        assertThrows(ForbiddenException.class,
                () -> service.hold(6L, new HoldRequest(HoldReason.AWAITING_VENDOR, null), engineer));
    }

    // ---------------------------------------------------------------- resume

    @Test
    void resume_goesBackToInProgress_andClearsTheReason() {
        Incident incident = given(5L, IncidentState.ON_HOLD);
        incident.setHoldReason(HoldReason.AWAITING_PROBLEM);

        IncidentResponse response = service.resume(5L, engineer);

        assertEquals(IncidentState.IN_PROGRESS, response.state());
        assertNull(incident.getHoldReason());
    }

    @Test
    void resume_aRunningIncidentIsRefused() {
        given(5L, IncidentState.IN_PROGRESS);

        assertThrows(ConflictException.class, () -> service.resume(5L, engineer));
    }

    @Test
    void anIncidentOnHold_cannotBeResolvedOrClosed_untilItIsResumed() {
        given(5L, IncidentState.ON_HOLD);

        assertThrows(ConflictException.class,
                () -> service.resolve(5L, new ResolveRequest("Solution provided", "done"), engineer));
        assertThrows(ConflictException.class, () -> service.close(5L, engineer));
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancel_worksFromNew_inProgressAndOnHold() {
        for (IncidentState state : List.of(IncidentState.NEW, IncidentState.IN_PROGRESS, IncidentState.ON_HOLD)) {
            Incident incident = given(5L, state);
            incident.setHoldReason(state == IncidentState.ON_HOLD ? HoldReason.AWAITING_CALLER : null);

            IncidentResponse response = service.cancel(5L, engineer);

            assertEquals(IncidentState.CANCELLED, response.state());
            assertNull(incident.getHoldReason());
            assertNotNull(incident.getClosedAt());
        }
    }

    @Test
    void cancel_aFinishedIncidentIsRefused() {
        given(5L, IncidentState.RESOLVED);

        assertThrows(ConflictException.class, () -> service.cancel(5L, engineer));
    }

    @Test
    void cancel_byAnEngineerOfAnotherTeam_isForbidden() {
        given(6L, IncidentState.NEW);

        assertThrows(ForbiddenException.class, () -> service.cancel(6L, otherTeamEngineer));
    }

    @Test
    void anIncidentOnHold_canStillBeEditedAndReassigned() {
        Incident incident = given(5L, IncidentState.ON_HOLD);
        incident.setHoldReason(HoldReason.AWAITING_VENDOR);
        when(assignmentClient.teams()).thenReturn(List.of(new TeamDto(3L, "Database", "d")));

        service.update(5L, new UpdateIncidentRequest(null, null, 1, null, null), engineer);
        service.reassign(5L, 3L, engineer);

        assertEquals(IncidentState.NEW, incident.getState());
        assertNull(incident.getHoldReason());
        assertNull(incident.getAssignedToId());
    }

    // ----------------------------------------------------------- subcategory

    @Test
    void create_storesAValidSubcategory_inItsOfficialSpelling() {
        when(assignmentClient.resolve("Network")).thenReturn(new ResolveResponse(2L));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        IncidentResponse response = service.create(
                new CreateIncidentRequest("x", "y", "Network", " vpn ", 2, 2), employee);

        assertEquals("VPN", response.subcategory());
    }

    @Test
    void create_aSubcategoryOfAnotherCategory_isRejected() {
        when(assignmentClient.resolve("Network")).thenReturn(new ResolveResponse(2L));

        assertThrows(BadRequestException.class, () -> service.create(
                new CreateIncidentRequest("x", "y", "Network", "Oracle", 2, 2), employee));

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void create_aCategoryWithoutSubcategories_rejectsOne() {
        when(assignmentClient.resolve("Other")).thenReturn(new ResolveResponse(2L));

        assertThrows(BadRequestException.class, () -> service.create(
                new CreateIncidentRequest("x", "y", "Other", "Email", 2, 2), employee));
    }

    @Test
    void update_changingTheCategory_clearsASubcategoryThatNoLongerFits() {
        Incident incident = given(5L, IncidentState.NEW);
        incident.setSubcategory("DNS");

        service.update(5L, new UpdateIncidentRequest("Database", null, null, null, null), admin);

        assertNull(incident.getSubcategory());
    }

    @Test
    void update_changingTheCategory_keepsASubcategoryThatStillFits() {
        Incident incident = given(5L, IncidentState.NEW);
        incident.setSubcategory("Email");

        service.update(5L, new UpdateIncidentRequest("Software", null, null, null, null), admin);

        assertEquals("Email", incident.getSubcategory());
    }

    @Test
    void update_canSetAndClearTheSubcategory() {
        Incident incident = given(5L, IncidentState.NEW);

        service.update(5L, new UpdateIncidentRequest(null, "wireless", null, null, null), engineer);
        assertEquals("Wireless", incident.getSubcategory());

        service.update(5L, new UpdateIncidentRequest(null, "", null, null, null), engineer);
        assertNull(incident.getSubcategory());
    }

    // ------------------------------------------------- problem closed (D32)

    @Test
    void problemClosed_wakesAnIncidentOnHold_andAddsTheCloseNotesAsAWorkNote() {
        Incident incident = given(5L, IncidentState.ON_HOLD);
        incident.setHoldReason(HoldReason.AWAITING_PROBLEM);

        InternalIncidentResponse response = service.problemClosed(5L, new ProblemClosedRequest("PRB0000003", "Router replaced"), admin);

        assertEquals("IN_PROGRESS", response.state());
        assertNull(incident.getHoldReason());
        ArgumentCaptor<IncidentComment> saved = ArgumentCaptor.forClass(IncidentComment.class);
        verify(commentRepository).save(saved.capture());
        assertEquals(CommentType.WORK_NOTE, saved.getValue().getType());
        assertTrue(saved.getValue().getBody().contains("PRB0000003"));
        assertTrue(saved.getValue().getBody().contains("Router replaced"));
    }

    @Test
    void problemClosed_leavesARunningIncidentRunning_butStillLeavesTheNote() {
        given(5L, IncidentState.IN_PROGRESS);

        InternalIncidentResponse response = service.problemClosed(5L, new ProblemClosedRequest("PRB0000003", "Router replaced"), admin);

        assertEquals("IN_PROGRESS", response.state());
        verify(commentRepository).save(any(IncidentComment.class));
    }

    @Test
    void problemClosed_doesNothingToAFinishedIncident() {
        given(5L, IncidentState.CLOSED);

        InternalIncidentResponse response = service.problemClosed(5L, new ProblemClosedRequest("PRB0000003", "Router replaced"), admin);

        assertEquals("CLOSED", response.state());
        verify(commentRepository, never()).save(any());
    }

    @Test
    void problemClosed_withoutCloseNotes_addsNoNote() {
        Incident incident = given(5L, IncidentState.ON_HOLD);

        service.problemClosed(5L, new ProblemClosedRequest("PRB0000003", null), admin);

        assertEquals(IncidentState.IN_PROGRESS, incident.getState());
        verify(commentRepository, never()).save(any());
    }
}
