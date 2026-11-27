package com.icc.majorincident.service;

import com.icc.common.audit.AuditService;
import com.icc.common.error.BadRequestException;
import com.icc.common.error.ConflictException;
import com.icc.common.error.NotFoundException;
import com.icc.common.jwt.JwtClaims;
import com.icc.common.jwt.Role;
import com.icc.majorincident.client.IncidentClient;
import com.icc.majorincident.client.IncidentClient.IncidentDto;
import com.icc.majorincident.client.IncidentClient.LinkRequest;
import com.icc.majorincident.dto.DeclareMajorIncidentRequest;
import com.icc.majorincident.dto.MajorIncidentResponse;
import com.icc.majorincident.dto.MajorIncidentStatsResponse;
import com.icc.majorincident.dto.TimelineEntryResponse;
import com.icc.majorincident.entity.MajorIncident;
import com.icc.majorincident.entity.MajorIncidentStatus;
import com.icc.majorincident.entity.TimelineEntry;
import com.icc.majorincident.entity.TimelineEntryType;
import com.icc.majorincident.mapper.MajorIncidentMapper;
import com.icc.majorincident.repository.MajorIncidentRepository;
import com.icc.majorincident.repository.TimelineEntryRepository;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MajorIncidentServiceTest {

    @Mock
    private MajorIncidentRepository repository;
    @Mock
    private TimelineEntryRepository timelineRepository;
    @Mock
    private IncidentClient incidentClient;
    @Mock
    private AuditService auditService;

    private MajorIncidentService service;
    private final JwtClaims engineer = new JwtClaims(3L, "net.engineer", Role.SUPPORT_ENGINEER, 2L);

    @BeforeEach
    void setUp() {
        service = new MajorIncidentService(repository, timelineRepository, incidentClient,
                new MajorIncidentMapper(), auditService);
    }

    private IncidentDto incident(int priority, Long majorIncidentId) {
        return new IncidentDto(5L, "INC0000005", "Core switch down", priority, "IN_PROGRESS", majorIncidentId);
    }

    private MajorIncident majorIncident(Long id, MajorIncidentStatus status) {
        MajorIncident m = new MajorIncident(5L, "Network outage", "All offices", 3L, 3L);
        ReflectionTestUtils.setField(m, "id", id);
        m.setStatus(status);
        return m;
    }

    private FeignException feignNotFound() {
        Request request = Request.create(Request.HttpMethod.GET, "http://incident-service/x", Map.of(),
                null, StandardCharsets.UTF_8, new RequestTemplate());
        Response response = Response.builder().status(404).reason("nf").request(request).headers(Map.of()).build();
        return FeignException.errorStatus("IncidentClient#get", response);
    }

    // --------------------------------------------------------------- declare

    @Test
    void declare_aPriority1Incident_isSaved_andLinkedBackToTheIncident() {
        when(incidentClient.get(5L)).thenReturn(incident(1, null));
        when(repository.existsByIncidentId(5L)).thenReturn(false);
        when(repository.save(any(MajorIncident.class))).thenAnswer(invocation -> {
            MajorIncident saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 40L);
            return saved;
        });

        MajorIncidentResponse response = service.declare(
                new DeclareMajorIncidentRequest(5L, " Network outage ", "All offices are offline", null), engineer);

        assertEquals(40L, response.id());
        assertEquals(MajorIncidentStatus.DECLARED, response.status());
        assertEquals("Network outage", response.summary());
        assertEquals(3L, response.commanderId(), "the person who declares is the default commander");
        verify(incidentClient).linkMajorIncident(5L, new LinkRequest(40L));
        verify(auditService).record(eq(3L), eq("MAJOR_INCIDENT_DECLARED"), eq("MAJOR_INCIDENT"), eq(40L), anyString());

        ArgumentCaptor<TimelineEntry> entry = ArgumentCaptor.forClass(TimelineEntry.class);
        verify(timelineRepository).save(entry.capture());
        assertEquals(TimelineEntryType.STATUS_CHANGE, entry.getValue().getEntryType());
    }

    @Test
    void declare_anExplicitCommanderIsKept() {
        when(incidentClient.get(5L)).thenReturn(incident(1, null));
        when(repository.save(any(MajorIncident.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MajorIncidentResponse response = service.declare(
                new DeclareMajorIncidentRequest(5L, "s", "i", 8L), engineer);

        assertEquals(8L, response.commanderId());
    }

    @Test
    void declare_onlyAPriority1IncidentCanBeEscalated() {
        when(incidentClient.get(5L)).thenReturn(incident(2, null));

        assertThrows(ConflictException.class,
                () -> service.declare(new DeclareMajorIncidentRequest(5L, "s", "i", null), engineer));

        verify(repository, never()).save(any());
        verify(incidentClient, never()).linkMajorIncident(any(), any());
    }

    @Test
    void declare_anIncidentThatAlreadyHasAMajorIncidentIsRefused() {
        when(incidentClient.get(5L)).thenReturn(incident(1, 33L));

        assertThrows(ConflictException.class,
                () -> service.declare(new DeclareMajorIncidentRequest(5L, "s", "i", null), engineer));
    }

    @Test
    void declare_alsoChecksOurOwnTable() {
        when(incidentClient.get(5L)).thenReturn(incident(1, null));
        when(repository.existsByIncidentId(5L)).thenReturn(true);

        assertThrows(ConflictException.class,
                () -> service.declare(new DeclareMajorIncidentRequest(5L, "s", "i", null), engineer));
    }

    @Test
    void declare_anUnknownIncidentIsNotFound() {
        when(incidentClient.get(99L)).thenThrow(feignNotFound());

        assertThrows(NotFoundException.class,
                () -> service.declare(new DeclareMajorIncidentRequest(99L, "s", "i", null), engineer));
    }

    // ------------------------------------------------------------------ list

    @Test
    void list_anUnknownStatusIsRejected() {
        assertThrows(BadRequestException.class, () -> service.list("BROKEN", 0, 20));
    }

    // ---------------------------------------------------------- timeline entry

    @Test
    void addTimelineEntry_savesAnUpdate() {
        when(repository.findById(40L)).thenReturn(Optional.of(majorIncident(40L, MajorIncidentStatus.MITIGATING)));
        when(timelineRepository.save(any(TimelineEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        TimelineEntryResponse entry = service.addTimelineEntry(40L, " Failover started ", engineer);

        assertEquals(TimelineEntryType.UPDATE, entry.entryType());
        assertEquals("Failover started", entry.text());
        assertEquals(3L, entry.authorId());
    }

    @Test
    void addTimelineEntry_afterClosing_isRefused() {
        when(repository.findById(40L)).thenReturn(Optional.of(majorIncident(40L, MajorIncidentStatus.CLOSED)));

        assertThrows(ConflictException.class, () -> service.addTimelineEntry(40L, "late", engineer));
    }

    // ---------------------------------------------------------------- status

    @Test
    void changeStatus_movesOneStepAtATime_andWritesTheTimeline() {
        MajorIncident m = majorIncident(40L, MajorIncidentStatus.DECLARED);
        when(repository.findById(40L)).thenReturn(Optional.of(m));

        MajorIncidentResponse response = service.changeStatus(40L, MajorIncidentStatus.MITIGATING, engineer);

        assertEquals(MajorIncidentStatus.MITIGATING, response.status());
        ArgumentCaptor<TimelineEntry> entry = ArgumentCaptor.forClass(TimelineEntry.class);
        verify(timelineRepository).save(entry.capture());
        assertEquals("Status changed from DECLARED to MITIGATING", entry.getValue().getText());
        verify(auditService).record(eq(3L), eq("MAJOR_INCIDENT_STATUS_CHANGED"), eq("MAJOR_INCIDENT"),
                eq(40L), eq("DECLARED to MITIGATING"));
    }

    @Test
    void changeStatus_skippingAStepIsRefused() {
        when(repository.findById(40L)).thenReturn(Optional.of(majorIncident(40L, MajorIncidentStatus.DECLARED)));

        assertThrows(ConflictException.class,
                () -> service.changeStatus(40L, MajorIncidentStatus.RESOLVED, engineer));
    }

    @Test
    void changeStatus_goingBackwardsIsRefused() {
        when(repository.findById(40L)).thenReturn(Optional.of(majorIncident(40L, MajorIncidentStatus.RESOLVED)));

        assertThrows(ConflictException.class,
                () -> service.changeStatus(40L, MajorIncidentStatus.DECLARED, engineer));
    }

    @Test
    void changeStatus_resolvingAndClosingSetTheirTimes() {
        MajorIncident m = majorIncident(40L, MajorIncidentStatus.MITIGATING);
        when(repository.findById(40L)).thenReturn(Optional.of(m));

        service.changeStatus(40L, MajorIncidentStatus.RESOLVED, engineer);
        assertNotNull(m.getResolvedAt());

        service.changeStatus(40L, MajorIncidentStatus.CLOSED, engineer);
        assertNotNull(m.getClosedAt());
    }

    @Test
    void changeStatus_nothingFollowsClosed() {
        when(repository.findById(40L)).thenReturn(Optional.of(majorIncident(40L, MajorIncidentStatus.CLOSED)));

        assertThrows(ConflictException.class,
                () -> service.changeStatus(40L, MajorIncidentStatus.CLOSED, engineer));
    }

    // ------------------------------------------------------------- internal

    @Test
    void getInternal_unknownIsNotFound() {
        when(repository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getInternal(99L));
    }

    @Test
    void stats_countsTheActiveOnes() {
        when(repository.count()).thenReturn(5L);
        when(repository.countGroupedByStatus()).thenReturn(List.<Object[]>of(
                new Object[]{MajorIncidentStatus.DECLARED, 1L},
                new Object[]{MajorIncidentStatus.MITIGATING, 2L},
                new Object[]{MajorIncidentStatus.CLOSED, 2L}));

        MajorIncidentStatsResponse stats = service.stats();

        assertEquals(5, stats.total());
        assertEquals(3, stats.active());
        assertEquals(2L, stats.byStatus().get("CLOSED"));
    }
}
