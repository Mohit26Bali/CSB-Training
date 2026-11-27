package com.icc.incident.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.ForbiddenException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.common.page.PageResponse;
import com.icc.incident.client.AssignmentClient;
import com.icc.incident.client.AssignmentClient.ResolveResponse;
import com.icc.incident.client.AssignmentClient.TeamDto;
import com.icc.incident.dto.AddCommentRequest;
import com.icc.incident.dto.CommentResponse;
import com.icc.incident.dto.CreateIncidentRequest;
import com.icc.incident.dto.IncidentDetailResponse;
import com.icc.incident.dto.IncidentResponse;
import com.icc.incident.dto.ReopenRequest;
import com.icc.incident.dto.ResolveRequest;
import com.icc.incident.dto.UpdateIncidentRequest;
import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.itsm.ItsmSettings;
import com.icc.incident.mapper.IncidentMapper;
import com.icc.incident.repository.IncidentCommentRepository;
import com.icc.incident.repository.IncidentRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

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

    /** An incident reported by user 6 and assigned to team 2, with the given state. */
    private Incident incident(Long id, IncidentState state) {
        Incident incident = new Incident("Switch down", "The core switch is down", "Network", 2, 2, 3, 6L, 2L);
        ReflectionTestUtils.setField(incident, "id", id);
        incident.setNumber(String.format("INC%07d", id));
        incident.setState(state);
        return incident;
    }

    private void given(Incident incident) {
        when(incidentRepository.findById(incident.getId())).thenReturn(Optional.of(incident));
    }

    private FeignException feignNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "http://assignment-service/x", Map.of(),
                null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder().status(404).reason("nf").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("AssignmentClient#resolve", response);
    }

    // ---------------------------------------------------------------- create

    @Test
    void create_calculatesPriority_routesToTheTeam_andNumbersTheIncident() {
        when(assignmentClient.resolve("Network")).thenReturn(new ResolveResponse(2L));
        when(incidentRepository.save(any(Incident.class))).thenAnswer(invocation -> {
            Incident saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 12L);
            return saved;
        });

        IncidentResponse response = service.create(
                new CreateIncidentRequest("  Switch down ", "Core switch is down", "network", null, 1, 1), employee);

        assertEquals("INC0000012", response.number());
        assertEquals(1, response.priority());
        assertEquals("Network", response.category());
        assertEquals(2L, response.assignedTeamId());
        assertEquals(6L, response.reporterId());
        assertEquals(IncidentState.NEW, response.state());
        assertEquals("Switch down", response.shortDescription());
        verify(auditService).record(eq(6L), eq("INCIDENT_CREATED"), eq("INCIDENT"), eq(12L), anyString());
    }

    @Test
    void create_withAnUnknownCategory_isRejected_andNothingIsSaved() {
        assertThrows(BadRequestException.class, () -> service.create(
                new CreateIncidentRequest("x", "y", "Printing", null, 2, 2), employee));

        verify(incidentRepository, never()).save(any());
    }

    @Test
    void create_whenNoTeamHandlesTheCategory_isRejected() {
        when(assignmentClient.resolve("Other")).thenThrow(feignNotFound());

        assertThrows(BadRequestException.class, () -> service.create(
                new CreateIncidentRequest("x", "y", "Other", null, 2, 2), employee));

        verify(incidentRepository, never()).save(any());
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_anEmployeeCannotAskForAllIncidents() {
        assertThrows(ForbiddenException.class, () -> service.list("all", null, null, 0, 20, employee));
        assertThrows(ForbiddenException.class, () -> service.list("assigned", null, null, 0, 20, employee));
    }

    @Test
    void list_anUnknownScopeOrStateIsRejected() {
        assertThrows(BadRequestException.class, () -> service.list("everything", null, null, 0, 20, engineer));
        assertThrows(BadRequestException.class, () -> service.list("mine", "BROKEN", null, 0, 20, engineer));
    }

    @Test
    @SuppressWarnings("unchecked")
    void list_returnsAPageOfIncidents() {
        when(incidentRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(incident(5L, IncidentState.NEW))));

        PageResponse<IncidentResponse> page = service.list("assigned", "new", "switch", 0, 20, engineer);

        assertEquals(1, page.total());
        assertEquals("INC0000005", page.items().get(0).number());
    }

    // ------------------------------------------------------------------- get

    @Test
    void get_anEmployeeCannotSeeSomeoneElsesIncident() {
        given(incident(5L, IncidentState.NEW));
        JwtClaims other = new JwtClaims(7L, "employee2", Role.EMPLOYEE, null);

        assertThrows(NotFoundException.class, () -> service.get(5L, other));
    }

    @Test
    void get_anEmployeeSeesCommentsButNeverWorkNotes() {
        given(incident(5L, IncidentState.IN_PROGRESS));
        when(commentRepository.findByIncidentIdAndTypeOrderByIdAsc(5L, CommentType.COMMENT))
                .thenReturn(List.of(new IncidentComment(5L, CommentType.COMMENT, "Hello", 6L)));

        IncidentDetailResponse detail = service.get(5L, employee);

        assertEquals(1, detail.comments().size());
        verify(commentRepository, never()).findByIncidentIdOrderByIdAsc(anyLong());
    }

    @Test
    void get_anEngineerSeesEverything() {
        given(incident(5L, IncidentState.IN_PROGRESS));
        when(commentRepository.findByIncidentIdOrderByIdAsc(5L)).thenReturn(List.of(
                new IncidentComment(5L, CommentType.COMMENT, "Hello", 6L),
                new IncidentComment(5L, CommentType.WORK_NOTE, "Checking the cable", 3L)));

        IncidentDetailResponse detail = service.get(5L, engineer);

        assertEquals(2, detail.comments().size());
    }

    @Test
    void get_unknownIncident_isNotFound() {
        when(incidentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(99L, engineer));
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_recalculatesThePriority_neverTakesItFromTheClient() {
        Incident incident = incident(5L, IncidentState.NEW);
        given(incident);

        IncidentResponse response = service.update(5L, new UpdateIncidentRequest(null, null, 1, 1, null), engineer);

        assertEquals(1, response.priority());
        assertEquals(1, incident.getImpact());
        verify(auditService).record(eq(3L), eq("INCIDENT_UPDATED"), eq("INCIDENT"), eq(5L), anyString());
    }

    @Test
    void update_onlyChangesTheFieldsThatAreSent() {
        Incident incident = incident(5L, IncidentState.NEW);
        given(incident);

        service.update(5L, new UpdateIncidentRequest("Database", null, null, null, null), admin);

        assertEquals("Database", incident.getCategory());
        assertEquals(2, incident.getImpact());
        assertEquals("The core switch is down", incident.getDescription());
    }

    @Test
    void update_aResolvedIncidentCannotBeEdited() {
        given(incident(5L, IncidentState.RESOLVED));

        assertThrows(ConflictException.class,
                () -> service.update(5L, new UpdateIncidentRequest(null, null, 1, null, null), engineer));
    }

    // --------------------------------------------------------------- comment

    @Test
    void addComment_anEmployeeCannotWriteAWorkNote() {
        given(incident(5L, IncidentState.NEW));

        assertThrows(ForbiddenException.class,
                () -> service.addComment(5L, new AddCommentRequest(CommentType.WORK_NOTE, "secret"), employee));
        verify(commentRepository, never()).save(any());
    }

    @Test
    void addComment_anEmployeeCannotCommentOnSomeoneElsesIncident() {
        given(incident(5L, IncidentState.NEW));
        JwtClaims other = new JwtClaims(7L, "employee2", Role.EMPLOYEE, null);

        assertThrows(NotFoundException.class,
                () -> service.addComment(5L, new AddCommentRequest(CommentType.COMMENT, "hi"), other));
    }

    @Test
    void addComment_anEngineerCanWriteAWorkNote() {
        given(incident(5L, IncidentState.IN_PROGRESS));
        when(commentRepository.save(any(IncidentComment.class))).thenAnswer(i -> i.getArgument(0));

        CommentResponse response = service.addComment(5L,
                new AddCommentRequest(CommentType.WORK_NOTE, " Replacing the cable "), engineer);

        assertEquals(CommentType.WORK_NOTE, response.type());
        assertEquals("Replacing the cable", response.body());
        assertEquals(3L, response.authorId());
    }

    @Test
    void addComment_onAClosedIncident_isRejected_closedIsFinal() {
        given(incident(5L, IncidentState.CLOSED));

        assertThrows(ConflictException.class,
                () -> service.addComment(5L, new AddCommentRequest(CommentType.COMMENT, "still broken"), engineer));
    }

    @Test
    void addComment_onAResolvedIncident_isAllowed_soItCanBeFollowedUpAndReopened() {
        given(incident(5L, IncidentState.RESOLVED));
        when(commentRepository.save(any(IncidentComment.class))).thenAnswer(i -> i.getArgument(0));

        CommentResponse response = service.addComment(5L, new AddCommentRequest(CommentType.COMMENT, "still broken"), engineer);

        assertEquals("still broken", response.body());
    }

    @Test
    void addComment_onACancelledIncident_isRejected() {
        given(incident(5L, IncidentState.CANCELLED));

        assertThrows(ConflictException.class,
                () -> service.addComment(5L, new AddCommentRequest(CommentType.COMMENT, "hi"), engineer));
    }

    // ---------------------------------------------------------------- reopen

    @Test
    void reopen_aResolvedIncident_goesBackInProgress_toTheEngineerWhoHadIt() {
        Incident incident = incident(5L, IncidentState.RESOLVED);
        incident.setAssignedToId(3L);
        incident.setResolvedAt(java.time.LocalDateTime.now());
        incident.setClosedAt(java.time.LocalDateTime.now());
        given(incident);

        IncidentResponse response = service.reopen(5L, new ReopenRequest(" Still failing "), engineer);

        assertEquals(IncidentState.IN_PROGRESS, response.state());
        assertEquals(3L, incident.getAssignedToId());
        assertNull(incident.getResolvedAt());
        assertNull(incident.getClosedAt());
        // The reason is saved as a comment the reporter can see.
        verify(commentRepository).save(argThat(c -> c.getType() == CommentType.COMMENT && c.getBody().equals("Reopened: Still failing")));
        verify(auditService).record(eq(3L), eq("INCIDENT_REOPENED"), eq("INCIDENT"), eq(5L), eq("From RESOLVED"));
    }

    @Test
    void reopen_aResolvedIncidentThatNobodyHad_goesBackToNew() {
        given(incident(5L, IncidentState.RESOLVED));

        IncidentResponse response = service.reopen(5L, new ReopenRequest("Wrong fix"), engineer);

        assertEquals(IncidentState.NEW, response.state());
    }

    @Test
    void reopen_byTheReporter_isAllowed() {
        given(incident(5L, IncidentState.RESOLVED));

        assertEquals(IncidentState.NEW, service.reopen(5L, new ReopenRequest("It came back"), employee).state());
    }

    @Test
    void reopen_byAnotherEmployee_isNotFound_theIncidentIsNotTheirs() {
        given(incident(5L, IncidentState.RESOLVED));
        JwtClaims otherEmployee = new JwtClaims(7L, "employee2", Role.EMPLOYEE, null);

        assertThrows(NotFoundException.class, () -> service.reopen(5L, new ReopenRequest("x"), otherEmployee));
    }

    @Test
    void reopen_byAnEngineerOfAnotherTeam_isForbidden() {
        given(incident(5L, IncidentState.RESOLVED));

        assertThrows(ForbiddenException.class, () -> service.reopen(5L, new ReopenRequest("x"), otherTeamEngineer));
    }

    @Test
    void reopen_anOpenOrCancelledIncident_isRefused() {
        for (IncidentState state : new IncidentState[]{IncidentState.NEW, IncidentState.IN_PROGRESS, IncidentState.CLOSED, IncidentState.CANCELLED}) {
            given(incident(5L, state));
            assertThrows(ConflictException.class, () -> service.reopen(5L, new ReopenRequest("x"), engineer));
        }
        verify(commentRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- accept

    @Test
    void accept_movesToInProgress_andAssignsTheEngineer() {
        Incident incident = incident(5L, IncidentState.NEW);
        given(incident);

        IncidentResponse response = service.accept(5L, engineer);

        assertEquals(IncidentState.IN_PROGRESS, response.state());
        assertEquals(3L, response.assignedToId());
        verify(auditService).record(eq(3L), eq("INCIDENT_ACCEPTED"), eq("INCIDENT"), eq(5L), isNull());
    }

    @Test
    void accept_anEngineerOfAnotherTeamIsRefused() {
        given(incident(5L, IncidentState.NEW));

        assertThrows(ForbiddenException.class, () -> service.accept(5L, otherTeamEngineer));
    }

    @Test
    void accept_onlyFromNew() {
        given(incident(5L, IncidentState.IN_PROGRESS));

        assertThrows(ConflictException.class, () -> service.accept(5L, engineer));
    }

    // --------------------------------------------------------------- resolve

    @Test
    void resolve_needsTheAcceptingEngineer_andStoresTheResolution() {
        Incident incident = incident(5L, IncidentState.IN_PROGRESS);
        incident.setAssignedToId(3L);
        given(incident);

        IncidentResponse response = service.resolve(5L, new ResolveRequest(" solution PROVIDED ", " Cable replaced "), engineer);

        assertEquals(IncidentState.RESOLVED, response.state());
        // The code is stored in the official spelling, whatever the case that was typed.
        assertEquals("Solution provided", response.resolutionCode());
        assertEquals("Cable replaced", response.resolutionNotes());
        assertNotNull(response.resolvedAt());
    }

    @Test
    void resolve_withACodeThatIsNotInTheList_isRejected_andNothingChanges() {
        Incident incident = incident(5L, IncidentState.IN_PROGRESS);
        incident.setAssignedToId(3L);
        given(incident);

        assertThrows(BadRequestException.class, () -> service.resolve(5L, new ResolveRequest("Solved", "done"), engineer));
        assertThrows(BadRequestException.class, () -> service.resolve(5L, new ResolveRequest("anything I like", "done"), engineer));

        assertEquals(IncidentState.IN_PROGRESS, incident.getState());
        assertNull(incident.getResolutionCode());
    }

    @Test
    void resolve_acceptsEveryOneOfTheTenCodes() {
        for (String code : ResolutionCodes.ALL) {
            Incident incident = incident(5L, IncidentState.IN_PROGRESS);
            incident.setAssignedToId(3L);
            given(incident);

            assertEquals(code, service.resolve(5L, new ResolveRequest(code, "done"), engineer).resolutionCode());
        }
        assertEquals(10, ResolutionCodes.ALL.size());
    }

    @Test
    void resolve_anotherEngineerCannotResolveIt() {
        Incident incident = incident(5L, IncidentState.IN_PROGRESS);
        incident.setAssignedToId(3L);
        given(incident);
        JwtClaims colleague = new JwtClaims(8L, "net.engineer2", Role.SUPPORT_ENGINEER, 2L);

        assertThrows(ForbiddenException.class, () -> service.resolve(5L, new ResolveRequest("a", "b"), colleague));
    }

    @Test
    void resolve_aNewIncidentCannotBeResolvedDirectly() {
        given(incident(5L, IncidentState.NEW));

        assertThrows(ConflictException.class, () -> service.resolve(5L, new ResolveRequest("a", "b"), engineer));
    }

    // ----------------------------------------------------------------- close

    @Test
    void close_onlyFromResolved() {
        Incident resolved = incident(5L, IncidentState.RESOLVED);
        given(resolved);

        IncidentResponse response = service.close(5L, engineer);

        assertEquals(IncidentState.CLOSED, response.state());
        assertNotNull(response.closedAt());
    }

    @Test
    void close_anInProgressIncidentCannotBeClosed() {
        given(incident(5L, IncidentState.IN_PROGRESS));

        assertThrows(ConflictException.class, () -> service.close(5L, engineer));
    }

    // -------------------------------------------------------------- reassign

    @Test
    void reassign_goesBackToNew_withNobodyAssigned() {
        Incident incident = incident(5L, IncidentState.IN_PROGRESS);
        incident.setAssignedToId(3L);
        given(incident);
        when(assignmentClient.teams()).thenReturn(List.of(new TeamDto(2L, "Network", "n"), new TeamDto(3L, "Database", "d")));

        IncidentResponse response = service.reassign(5L, 3L, engineer);

        assertEquals(3L, response.assignedTeamId());
        assertNull(response.assignedToId());
        assertEquals(IncidentState.NEW, response.state());
        verify(auditService).record(eq(3L), eq("INCIDENT_REASSIGNED"), eq("INCIDENT"), eq(5L), eq("Team 2 to team 3"));
    }

    @Test
    void reassign_aResolvedIncidentCannotBeMoved() {
        given(incident(5L, IncidentState.RESOLVED));

        assertThrows(ConflictException.class, () -> service.reassign(5L, 3L, engineer));
    }

    @Test
    void reassign_toAnUnknownTeamIsRejected() {
        given(incident(5L, IncidentState.NEW));
        when(assignmentClient.teams()).thenReturn(List.of(new TeamDto(2L, "Network", "n")));

        assertThrows(BadRequestException.class, () -> service.reassign(5L, 99L, engineer));
    }

    // ---------------------------------------------------- major incident link

    @Test
    void linkMajorIncident_storesTheLink() {
        Incident incident = incident(5L, IncidentState.IN_PROGRESS);
        given(incident);

        service.linkMajorIncident(5L, 40L, engineer);

        assertEquals(40L, incident.getMajorIncidentId());
        verify(auditService).record(eq(3L), eq("MAJOR_INCIDENT_LINKED"), eq("INCIDENT"), eq(5L), anyString());
    }

    @Test
    void linkMajorIncident_anIncidentHasAtMostOne() {
        Incident incident = incident(5L, IncidentState.IN_PROGRESS);
        incident.setMajorIncidentId(40L);
        given(incident);

        assertThrows(ConflictException.class, () -> service.linkMajorIncident(5L, 41L, engineer));
    }

    @Test
    void getInternal_hidesTheEnumBehindPlainText() {
        given(incident(5L, IncidentState.RESOLVED));

        assertEquals("RESOLVED", service.getInternal(5L).state());
    }
}
