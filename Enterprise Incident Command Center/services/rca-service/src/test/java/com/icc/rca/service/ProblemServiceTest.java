package com.icc.rca.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.rca.client.IncidentClient;
import com.icc.rca.client.IncidentClient.IncidentDto;
import com.icc.rca.client.IncidentClient.ProblemClosedBody;
import com.icc.rca.client.MajorIncidentClient;
import com.icc.rca.dto.AcceptRiskRequest;
import com.icc.rca.dto.ActionResponse;
import com.icc.rca.dto.AddActionRequest;
import com.icc.rca.dto.AssessRequest;
import com.icc.rca.dto.CreateProblemRequest;
import com.icc.rca.dto.FixRequest;
import com.icc.rca.dto.MarkDuplicateRequest;
import com.icc.rca.dto.ProblemDetailResponse;
import com.icc.rca.dto.ProblemResponse;
import com.icc.rca.dto.ProblemStatsResponse;
import com.icc.rca.dto.UpdateProblemRequest;
import com.icc.rca.entity.ActionStatus;
import com.icc.rca.entity.CorrectiveAction;
import com.icc.rca.entity.Problem;
import com.icc.rca.entity.ProblemAction;
import com.icc.rca.entity.ProblemIncident;
import com.icc.rca.entity.ProblemStatus;
import com.icc.rca.entity.ResolutionCode;
import com.icc.rca.mapper.ProblemMapper;
import com.icc.rca.repository.CorrectiveActionRepository;
import com.icc.rca.repository.ProblemIncidentRepository;
import com.icc.rca.repository.ProblemRepository;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProblemServiceTest {

    @Mock
    private ProblemRepository problemRepository;
    @Mock
    private CorrectiveActionRepository actionRepository;
    @Mock
    private ProblemIncidentRepository linkRepository;
    @Mock
    private IncidentClient incidentClient;
    @Mock
    private MajorIncidentClient majorIncidentClient;
    @Mock
    private AuditService auditService;

    private ProblemService service;
    private final JwtClaims admin = new JwtClaims(1L, "admin", Role.ADMIN, null);

    @BeforeEach
    void setUp() {
        service = new ProblemService(problemRepository, actionRepository, linkRepository, incidentClient,
                majorIncidentClient, new ProblemMapper(), auditService);
    }

    private Problem problem(Long id, ProblemStatus status) {
        Problem p = new Problem("Switch firmware", "Firmware bug", null, 1L);
        ReflectionTestUtils.setField(p, "id", id);
        p.setNumber(String.format("PRB%07d", id));
        p.setStatus(status);
        return p;
    }

    private Problem stored(Long id, ProblemStatus status) {
        Problem p = problem(id, status);
        when(problemRepository.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    private IncidentDto incident(Long id, String state) {
        return new IncidentDto(id, String.format("INC%07d", id), "x", 1, state, null);
    }

    private FeignException feignNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "http://x/y", Map.of(),
                null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder().status(404).reason("nf").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("Client#get", response);
    }

    // ---------------------------------------------------------------- create

    @Test
    void create_savesANewProblem_linksTheIncidents_andNumbersIt() {
        when(incidentClient.get(5L)).thenReturn(incident(5L, "RESOLVED"));
        when(incidentClient.get(6L)).thenReturn(incident(6L, "RESOLVED"));
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> {
            Problem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 3L);
            return saved;
        });

        ProblemResponse response = service.create(
                new CreateProblemRequest(" Switch firmware ", "Firmware bug", List.of(5L, 6L, 5L), null, null, null, null), admin);

        assertEquals("PRB0000003", response.number());
        assertEquals(ProblemStatus.NEW, response.status());
        assertEquals("Switch firmware", response.title());
        // Impact and urgency default to 3 (Low), which gives priority 5 (Planning).
        assertEquals(5, response.priority());
        assertEquals(List.of(ProblemAction.ASSESS), response.availableActions());
        // The duplicate id 5 is linked once.
        verify(linkRepository, times(2)).save(any(ProblemIncident.class));
        verify(auditService).record(eq(1L), eq("PROBLEM_CREATED"), eq("PROBLEM"), eq(3L), anyString());
    }

    @Test
    void create_withAnAssigneeAndImpact_startsAtAssess_andCalculatesThePriority() {
        when(incidentClient.get(5L)).thenReturn(incident(5L, "NEW"));
        when(problemRepository.save(any(Problem.class))).thenAnswer(invocation -> {
            Problem saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 4L);
            return saved;
        });

        ProblemResponse response = service.create(
                new CreateProblemRequest("t", "d", List.of(5L), null, 1, 2, 7L), admin);

        assertEquals(ProblemStatus.ASSESS, response.status());
        assertEquals(7L, response.assignedToId());
        // Impact 1 and urgency 2 give priority 2 (High), the same formula as an incident.
        assertEquals(2, response.priority());
    }

    @Test
    void create_withAnUnknownIncident_isNotFound_andNothingIsSaved() {
        when(incidentClient.get(99L)).thenThrow(feignNotFound());

        assertThrows(NotFoundException.class,
                () -> service.create(new CreateProblemRequest("t", "d", List.of(99L), null, null, null, null), admin));

        verify(problemRepository, never()).save(any());
    }

    @Test
    void create_withAnUnknownMajorIncident_isNotFound() {
        when(incidentClient.get(5L)).thenReturn(incident(5L, "RESOLVED"));
        when(majorIncidentClient.get(77L)).thenThrow(feignNotFound());

        assertThrows(NotFoundException.class,
                () -> service.create(new CreateProblemRequest("t", "d", List.of(5L), 77L, null, null, null), admin));
    }

    // ------------------------------------------------------------------- get

    @Test
    void get_returnsTheIncidentIdsAndActions() {
        stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);
        when(linkRepository.findByKeyProblemId(3L)).thenReturn(List.of(new ProblemIncident(3L, 6L), new ProblemIncident(3L, 5L)));
        when(actionRepository.findByProblemIdOrderByIdAsc(3L)).thenReturn(List.of(
                new CorrectiveAction(3L, "Upgrade firmware", 3L, LocalDate.of(2026, 12, 1))));

        ProblemDetailResponse detail = service.get(3L);

        assertEquals(List.of(5L, 6L), detail.incidentIds());
        assertEquals(1, detail.actions().size());
        assertEquals(ActionStatus.OPEN, detail.actions().get(0).status());
    }

    @Test
    void list_anUnknownStatusIsRejected() {
        assertThrows(BadRequestException.class, () -> service.list("BROKEN", 0, 20));
    }

    // ------------------------------------------------- the rulebook (ProblemAction)

    @Test
    void availableActions_followTheServiceNowButtons() {
        assertEquals(List.of(ProblemAction.ASSESS), ProblemAction.availableIn(ProblemStatus.NEW));
        assertEquals(List.of(ProblemAction.CONFIRM, ProblemAction.MARK_DUPLICATE, ProblemAction.CANCEL),
                ProblemAction.availableIn(ProblemStatus.ASSESS));
        assertEquals(List.of(ProblemAction.FIX, ProblemAction.ACCEPT_RISK, ProblemAction.MARK_DUPLICATE, ProblemAction.CANCEL),
                ProblemAction.availableIn(ProblemStatus.ROOT_CAUSE_ANALYSIS));
        assertEquals(List.of(ProblemAction.RESOLVE, ProblemAction.ACCEPT_RISK, ProblemAction.RE_ANALYZE),
                ProblemAction.availableIn(ProblemStatus.FIX_IN_PROGRESS));
        assertEquals(List.of(ProblemAction.COMPLETE, ProblemAction.RE_ANALYZE), ProblemAction.availableIn(ProblemStatus.RESOLVED));
        assertEquals(List.of(ProblemAction.RE_ANALYZE), ProblemAction.availableIn(ProblemStatus.CLOSED));
    }

    @Test
    void anActionInTheWrongStatus_isRefused_andNothingChanges() {
        Problem problem = stored(3L, ProblemStatus.NEW);

        assertThrows(ConflictException.class, () -> service.resolve(3L, admin));
        assertThrows(ConflictException.class, () -> service.complete(3L, admin));
        assertThrows(ConflictException.class, () -> service.confirm(3L, admin));

        assertEquals(ProblemStatus.NEW, problem.getStatus());
        verify(auditService, never()).record(any(), anyString(), anyString(), any(), anyString());
    }

    // ---------------------------------------------------------------- assess

    @Test
    void assess_needsAnAssignee() {
        Problem problem = stored(3L, ProblemStatus.NEW);

        assertThrows(ConflictException.class, () -> service.assess(3L, null, admin));
        assertThrows(ConflictException.class, () -> service.assess(3L, new AssessRequest(null), admin));

        assertEquals(ProblemStatus.NEW, problem.getStatus());
    }

    @Test
    void assess_withAnAssignee_movesToAssess() {
        Problem problem = stored(3L, ProblemStatus.NEW);

        ProblemResponse response = service.assess(3L, new AssessRequest(7L), admin);

        assertEquals(ProblemStatus.ASSESS, response.status());
        assertEquals(7L, problem.getAssignedToId());
        verify(auditService).record(eq(1L), eq("PROBLEM_ASSESS"), eq("PROBLEM"), eq(3L), anyString());
    }

    @Test
    void assess_whenTheProblemAlreadyHasAnAssignee_needsNoBody() {
        Problem problem = stored(3L, ProblemStatus.NEW);
        problem.setAssignedToId(7L);

        assertEquals(ProblemStatus.ASSESS, service.assess(3L, null, admin).status());
    }

    // --------------------------------------------------------------- confirm

    @Test
    void confirm_movesToRootCauseAnalysis() {
        stored(3L, ProblemStatus.ASSESS);

        assertEquals(ProblemStatus.ROOT_CAUSE_ANALYSIS, service.confirm(3L, admin).status());
    }

    // ------------------------------------------------------------------- fix

    @Test
    void fix_storesBothNotes_andMovesToFixInProgress() {
        Problem problem = stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);

        ProblemResponse response = service.fix(3L,
                new FixRequest(" Old router ", " Replace the router ", " Hardware ", null), admin);

        assertEquals(ProblemStatus.FIX_IN_PROGRESS, response.status());
        assertEquals("Old router", problem.getRootCause());
        assertEquals("Replace the router", problem.getFixNotes());
        assertEquals("Hardware", problem.getRootCauseCategory());
    }

    @Test
    void fix_refusesBlankNotes() {
        Problem problem = stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);

        assertThrows(BadRequestException.class, () -> service.fix(3L, new FixRequest("  ", "fix", null, null), admin));
        assertThrows(BadRequestException.class, () -> service.fix(3L, new FixRequest("cause", " ", null, null), admin));

        assertEquals(ProblemStatus.ROOT_CAUSE_ANALYSIS, problem.getStatus());
    }

    // --------------------------------------------------------------- resolve

    @Test
    void resolve_setsFixApplied_andTheResolvedTime() {
        Problem problem = stored(3L, ProblemStatus.FIX_IN_PROGRESS);
        problem.setFixNotes("Replaced the router");

        ProblemResponse response = service.resolve(3L, admin);

        assertEquals(ProblemStatus.RESOLVED, response.status());
        assertEquals(ResolutionCode.FIX_APPLIED, response.resolutionCode());
        assertNotNull(problem.getResolvedAt());
        assertNull(problem.getClosedAt());
    }

    @Test
    void resolve_needsTheFixNotes() {
        stored(3L, ProblemStatus.FIX_IN_PROGRESS);

        assertThrows(ConflictException.class, () -> service.resolve(3L, admin));
    }

    // -------------------------------------------------------------- complete

    @Test
    void complete_closesTheProblem_andMarksItsOpenActionsDone() {
        Problem problem = stored(3L, ProblemStatus.RESOLVED);
        problem.setResolutionCode(ResolutionCode.FIX_APPLIED);
        CorrectiveAction open = new CorrectiveAction(3L, "Upgrade firmware", 3L, LocalDate.of(2026, 12, 1));
        CorrectiveAction done = new CorrectiveAction(3L, "Write it down", 3L, LocalDate.of(2026, 12, 1));
        done.setStatus(ActionStatus.DONE);
        when(actionRepository.findByProblemIdOrderByIdAsc(3L)).thenReturn(List.of(open, done));

        ProblemResponse response = service.complete(3L, admin);

        assertEquals(ProblemStatus.CLOSED, response.status());
        assertNotNull(problem.getClosedAt());
        // ServiceNow closes the problem's tasks with it; open actions do not block (D31).
        assertEquals(ActionStatus.DONE, open.getStatus());
        assertEquals(List.of(ProblemAction.RE_ANALYZE), response.availableActions());
    }

    // ------------------------------------------- telling the incidents (D32)

    @Test
    void closing_tellsEveryLinkedIncident_withTheFixNotes() {
        Problem problem = stored(3L, ProblemStatus.RESOLVED);
        problem.setFixNotes("Router replaced");
        when(linkRepository.findByKeyProblemId(3L)).thenReturn(List.of(new ProblemIncident(3L, 5L), new ProblemIncident(3L, 6L)));

        service.complete(3L, admin);

        ProblemClosedBody body = new ProblemClosedBody("PRB0000003", "Router replaced");
        verify(incidentClient).problemClosed(5L, body);
        verify(incidentClient).problemClosed(6L, body);
    }

    @Test
    void closing_aFailedCallToOneIncident_doesNotStopTheClosing_orTheOthers() {
        Problem problem = stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);
        when(linkRepository.findByKeyProblemId(3L)).thenReturn(List.of(new ProblemIncident(3L, 5L), new ProblemIncident(3L, 6L)));
        when(incidentClient.problemClosed(eq(5L), any())).thenThrow(new IllegalStateException("incident-service is down"));

        ProblemResponse response = service.cancel(3L, admin);

        assertEquals(ProblemStatus.CLOSED, response.status());
        assertNotNull(problem.getClosedAt());
        verify(incidentClient).problemClosed(eq(6L), any());
        verify(auditService).record(eq(1L), eq("PROBLEM_CANCEL"), eq("PROBLEM"), eq(3L),
                org.mockito.ArgumentMatchers.contains("1 incident(s) told, 1 failed"));
    }

    @Test
    void closing_asADuplicate_doesNotTellTheIncidents_becauseTheyMoveToTheOriginal() {
        stored(3L, ProblemStatus.ASSESS);
        stored(2L, ProblemStatus.ROOT_CAUSE_ANALYSIS);
        when(linkRepository.findByKeyProblemId(2L)).thenReturn(List.of());
        when(linkRepository.findByKeyProblemId(3L)).thenReturn(List.of(new ProblemIncident(3L, 5L)));

        service.markDuplicate(3L, new MarkDuplicateRequest(2L), admin);

        verify(incidentClient, never()).problemClosed(any(), any());
    }

    // ----------------------------------------------------------- accept risk

    @Test
    void acceptRisk_closesAtOnce_withRiskAccepted() {
        Problem problem = stored(3L, ProblemStatus.FIX_IN_PROGRESS);

        ProblemResponse response = service.acceptRisk(3L, null, admin);

        assertEquals(ProblemStatus.CLOSED, response.status());
        assertEquals(ResolutionCode.RISK_ACCEPTED, response.resolutionCode());
        assertFalse(problem.isWorkaroundApplied());
        assertNotNull(problem.getClosedAt());
    }

    @Test
    void acceptRisk_withAnAppliedWorkaround_needsTheWorkaroundText() {
        Problem problem = stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);

        assertThrows(ConflictException.class,
                () -> service.acceptRisk(3L, new AcceptRiskRequest(true, " "), admin));
        assertEquals(ProblemStatus.ROOT_CAUSE_ANALYSIS, problem.getStatus());

        ProblemResponse response = service.acceptRisk(3L, new AcceptRiskRequest(true, "Reboot weekly"), admin);

        assertTrue(response.workaroundApplied());
        assertEquals("Reboot weekly", response.workaround());
    }

    @Test
    void acceptRisk_isNotOfferedWhenTheProblemIsAlreadyResolved() {
        stored(3L, ProblemStatus.RESOLVED);

        assertThrows(ConflictException.class, () -> service.acceptRisk(3L, null, admin));
    }

    // -------------------------------------------------------- mark duplicate

    @Test
    void markDuplicate_closesAsDuplicate_andCopiesTheLinkedIncidentsToTheOriginal() {
        Problem problem = stored(3L, ProblemStatus.ASSESS);
        Problem original = stored(2L, ProblemStatus.ROOT_CAUSE_ANALYSIS);
        when(linkRepository.findByKeyProblemId(2L)).thenReturn(List.of(new ProblemIncident(2L, 5L)));
        when(linkRepository.findByKeyProblemId(3L)).thenReturn(List.of(new ProblemIncident(3L, 5L), new ProblemIncident(3L, 6L)));

        ProblemResponse response = service.markDuplicate(3L, new MarkDuplicateRequest(2L), admin);

        assertEquals(ProblemStatus.CLOSED, response.status());
        assertEquals(ResolutionCode.DUPLICATE, response.resolutionCode());
        assertEquals(2L, problem.getDuplicateOfId());
        // Incident 5 is already on the original, so only incident 6 is added.
        ArgumentCaptor<ProblemIncident> saved = ArgumentCaptor.forClass(ProblemIncident.class);
        verify(linkRepository).save(saved.capture());
        assertEquals(2L, saved.getValue().getKey().problemId());
        assertEquals(6L, saved.getValue().getKey().incidentId());
        assertEquals(ProblemStatus.ROOT_CAUSE_ANALYSIS, original.getStatus());
    }

    @Test
    void markDuplicate_ofItself_isRefused() {
        stored(3L, ProblemStatus.ASSESS);

        assertThrows(BadRequestException.class, () -> service.markDuplicate(3L, new MarkDuplicateRequest(3L), admin));
    }

    @Test
    void markDuplicate_ofAnUnknownProblem_isNotFound() {
        stored(3L, ProblemStatus.ASSESS);
        when(problemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.markDuplicate(3L, new MarkDuplicateRequest(99L), admin));
    }

    @Test
    void markDuplicate_ofAProblemThatIsItselfADuplicate_isRefused() {
        stored(3L, ProblemStatus.ASSESS);
        Problem other = stored(2L, ProblemStatus.CLOSED);
        other.setResolutionCode(ResolutionCode.DUPLICATE);

        assertThrows(ConflictException.class, () -> service.markDuplicate(3L, new MarkDuplicateRequest(2L), admin));
    }

    // ---------------------------------------------------------------- cancel

    @Test
    void cancel_closesWithCanceled() {
        stored(3L, ProblemStatus.ASSESS);

        ProblemResponse response = service.cancel(3L, admin);

        assertEquals(ProblemStatus.CLOSED, response.status());
        assertEquals(ResolutionCode.CANCELED, response.resolutionCode());
    }

    @Test
    void cancel_isNotAllowedOnceTheFixHasStarted() {
        stored(3L, ProblemStatus.FIX_IN_PROGRESS);

        assertThrows(ConflictException.class, () -> service.cancel(3L, admin));
    }

    // ------------------------------------------------------------ re-analyze

    @Test
    void reAnalyze_fromClosed_undoesTheEnding_butKeepsTheNotes() {
        Problem problem = stored(3L, ProblemStatus.CLOSED);
        problem.setRootCause("Old router");
        problem.setFixNotes("Replaced");
        problem.setResolutionCode(ResolutionCode.FIX_APPLIED);
        problem.setResolvedAt(java.time.LocalDateTime.now());
        problem.setClosedAt(java.time.LocalDateTime.now());

        ProblemResponse response = service.reAnalyze(3L, admin);

        assertEquals(ProblemStatus.ROOT_CAUSE_ANALYSIS, response.status());
        assertNull(problem.getResolutionCode());
        assertNull(problem.getResolvedAt());
        assertNull(problem.getClosedAt());
        assertEquals(1, problem.getReopenCount());
        assertEquals("Old router", problem.getRootCause());
        assertEquals("Replaced", problem.getFixNotes());
    }

    @Test
    void reAnalyze_fromFixInProgress_isNotACountedReopening() {
        Problem problem = stored(3L, ProblemStatus.FIX_IN_PROGRESS);

        assertEquals(ProblemStatus.ROOT_CAUSE_ANALYSIS, service.reAnalyze(3L, admin).status());
        assertEquals(0, problem.getReopenCount());
    }

    @Test
    void reAnalyze_ofAProblemInAssess_isRefused() {
        stored(3L, ProblemStatus.ASSESS);

        assertThrows(ConflictException.class, () -> service.reAnalyze(3L, admin));
    }

    // ---------------------------------------------------------------- update

    @Test
    void update_changesOnlyTheSentFields_andRecalculatesThePriority() {
        Problem problem = stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);

        ProblemResponse response = service.update(3L,
                new UpdateProblemRequest(1, 1, 9L, " Cause ", null, null, "Reboot"), admin);

        assertEquals(1, response.priority());
        assertEquals(9L, problem.getAssignedToId());
        assertEquals("Cause", problem.getRootCause());
        assertEquals("Reboot", problem.getWorkaround());
        assertNull(problem.getFixNotes());
    }

    @Test
    void update_ofAClosedProblem_isRefused() {
        stored(3L, ProblemStatus.CLOSED);

        assertThrows(ConflictException.class,
                () -> service.update(3L, new UpdateProblemRequest(null, null, null, "x", null, null, null), admin));
    }

    @Test
    void update_refusesBlankCauseNotes() {
        stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);

        assertThrows(BadRequestException.class,
                () -> service.update(3L, new UpdateProblemRequest(null, null, null, " ", null, null, null), admin));
    }

    // --------------------------------------------------------------- actions

    @Test
    void addAction_isSavedAsOpen() {
        stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);
        when(actionRepository.save(any(CorrectiveAction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ActionResponse response = service.addAction(3L,
                new AddActionRequest(" Upgrade firmware ", 3L, LocalDate.of(2026, 12, 1)), admin);

        assertEquals("Upgrade firmware", response.description());
        assertEquals(ActionStatus.OPEN, response.status());
    }

    @Test
    void addAction_toAClosedProblem_isRefused() {
        stored(3L, ProblemStatus.CLOSED);

        assertThrows(ConflictException.class, () -> service.addAction(3L,
                new AddActionRequest("x", 3L, LocalDate.of(2026, 12, 1)), admin));
    }

    @Test
    void updateAction_marksItDone() {
        stored(3L, ProblemStatus.FIX_IN_PROGRESS);
        CorrectiveAction action = new CorrectiveAction(3L, "Upgrade firmware", 3L, LocalDate.of(2026, 12, 1));
        when(actionRepository.findById(8L)).thenReturn(Optional.of(action));

        ActionResponse response = service.updateAction(3L, 8L, ActionStatus.DONE, admin);

        assertEquals(ActionStatus.DONE, response.status());
    }

    @Test
    void updateAction_anActionOfAnotherProblemIsNotFound() {
        stored(3L, ProblemStatus.ROOT_CAUSE_ANALYSIS);
        when(actionRepository.findById(8L)).thenReturn(Optional.of(
                new CorrectiveAction(4L, "Other problem", 3L, LocalDate.of(2026, 12, 1))));

        assertThrows(NotFoundException.class, () -> service.updateAction(3L, 8L, ActionStatus.DONE, admin));
    }

    // ----------------------------------------------------------------- stats

    @Test
    void stats_countsTheOpenOnes() {
        when(problemRepository.count()).thenReturn(5L);
        when(actionRepository.countByStatus(ActionStatus.OPEN)).thenReturn(3L);
        when(problemRepository.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{ProblemStatus.NEW, 1L},
                new Object[]{ProblemStatus.ROOT_CAUSE_ANALYSIS, 1L},
                new Object[]{ProblemStatus.RESOLVED, 1L},
                new Object[]{ProblemStatus.CLOSED, 2L}));

        ProblemStatsResponse stats = service.stats();

        assertEquals(5, stats.total());
        assertEquals(3, stats.open());
        assertEquals(3, stats.openActions());
    }
}
