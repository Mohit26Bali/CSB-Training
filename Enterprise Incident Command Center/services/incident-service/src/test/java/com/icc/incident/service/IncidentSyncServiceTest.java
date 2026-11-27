package com.icc.incident.service;

import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.SyncStatus;
import com.icc.incident.itsm.ItsmPort;
import com.icc.incident.itsm.ItsmPort.SyncOutcome;
import com.icc.incident.repository.IncidentCommentRepository;
import com.icc.incident.repository.IncidentRepository;
import com.icc.incident.service.IncidentSyncService.Report;
import com.icc.servicenow.ServiceNowException;
import com.icc.servicenow.ServiceNowException.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** One round of the sync, with ServiceNow replaced by a mock: what is stored, what is retried, what is given up (D9, D38). */
@ExtendWith(MockitoExtension.class)
class IncidentSyncServiceTest {

    @Mock
    private IncidentRepository incidents;
    @Mock
    private IncidentCommentRepository comments;
    @Mock
    private ItsmPort itsm;

    private IncidentSyncService sync;

    @BeforeEach
    void setUp() {
        sync = new IncidentSyncService(incidents, comments, itsm);
    }

    private Incident incident(Long id) {
        Incident incident = new Incident("Switch down", "d", "Network", 1, 1, 1, 6L, 2L);
        ReflectionTestUtils.setField(incident, "id", id);
        incident.setNumber(String.format("INC%07d", id));
        return incident;
    }

    private static ServiceNowException serviceNow(Kind kind, int status) {
        return new ServiceNowException(kind, status, "ServiceNow said no", null);
    }

    // ------------------------------------------------------------------ incidents

    @Test
    void aPendingIncident_isSent_itsIdsAreStored_andItBecomesSynced() {
        Incident incident = incident(12L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(incident));
        when(itsm.pushIncident(incident)).thenReturn(new SyncOutcome("s1", "INC0010005", "icc-incident-12"));

        Report report = sync.runOnce();

        assertEquals(1, report.synced());
        verify(incidents).saveExternalIds(12L, "s1", "INC0010005", "icc-incident-12");
        verify(incidents).markSynced(eq(12L), eq(incident.getUpdatedAt()), any(LocalDateTime.class));
    }

    @Test
    void ifNothingWaits_nothingIsSent() {
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());

        Report report = sync.runOnce();

        assertFalse(report.didAnything());
        verify(itsm, never()).pushIncident(any());
    }

    @Test
    void anInstanceThatCannotBeReached_keepsTheIncidentPending_andStopsTheRound() {
        Incident first = incident(12L);
        Incident second = incident(13L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(first, second));
        when(itsm.pushIncident(first)).thenThrow(serviceNow(Kind.UNREACHABLE, 0));

        Report report = sync.runOnce();

        assertTrue(report.stoppedEarly());
        // PENDING with the reason, so the next round tries again; the second incident was not even tried.
        verify(incidents).markSyncResult(eq(12L), eq(first.getUpdatedAt()), eq(SyncStatus.PENDING), contains("ServiceNow said no"));
        verify(itsm, never()).pushIncident(second);
        verify(incidents, never()).markSynced(anyLong(), any(), any());
        // Comments wait too: ServiceNow is not reachable.
        verify(comments, never()).findTop50BySyncStatusOrderByIdAsc(anyString());
    }

    @Test
    void aRefusal_isFailed_andTheNextIncidentIsStillSent() {
        Incident first = incident(12L);
        Incident second = incident(13L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(first, second));
        when(itsm.pushIncident(first)).thenThrow(serviceNow(Kind.REJECTED_VALUE, 500));
        when(itsm.pushIncident(second)).thenReturn(new SyncOutcome("s2", "INC0010006", "icc-incident-13"));
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());

        Report report = sync.runOnce();

        assertEquals(1, report.failed());
        assertEquals(1, report.synced());
        verify(incidents).markSyncResult(eq(12L), eq(first.getUpdatedAt()), eq(SyncStatus.FAILED), contains("ServiceNow said no"));
    }

    @Test
    void dataOfOurOwnThatCannotBeSent_isFailedWithItsReason() {
        Incident incident = incident(12L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(incident));
        when(itsm.pushIncident(incident)).thenThrow(new IllegalArgumentException("\"Fixed\" is not a ServiceNow close code"));
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());

        sync.runOnce();

        verify(incidents).markSyncResult(eq(12L), eq(incident.getUpdatedAt()), eq(SyncStatus.FAILED), contains("not a ServiceNow close code"));
    }

    @Test
    void aVeryLongReason_isCutToWhatTheColumnHolds() {
        Incident incident = incident(12L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(incident));
        when(itsm.pushIncident(incident)).thenThrow(new IllegalArgumentException("x".repeat(900)));
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());

        sync.runOnce();

        verify(incidents).markSyncResult(eq(12L), any(), eq(SyncStatus.FAILED), org.mockito.ArgumentMatchers.argThat(text -> text.length() == 500));
    }

    // ------------------------------------------------------------------ comments

    @Test
    void aWaitingComment_isSent_onceItsIncidentIsInServiceNow() {
        Incident incident = incident(12L);
        ReflectionTestUtils.setField(incident, "externalId", "s1");
        IncidentComment comment = new IncidentComment(12L, CommentType.COMMENT, "hello", 3L);
        ReflectionTestUtils.setField(comment, "id", 40L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(comment));
        when(incidents.findById(12L)).thenReturn(Optional.of(incident));

        Report report = sync.runOnce();

        assertEquals(1, report.comments());
        verify(itsm).pushComment(incident, comment);
        verify(comments).markSynced(40L);
    }

    @Test
    void aComment_waitsForItsIncident_whenTheIncidentIsNotInServiceNowYet() {
        Incident incident = incident(12L);
        IncidentComment comment = new IncidentComment(12L, CommentType.COMMENT, "hello", 3L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(comment));
        when(incidents.findById(12L)).thenReturn(Optional.of(incident));

        sync.runOnce();

        verify(itsm, never()).pushComment(any(), any());
        verify(comments, never()).markSynced(anyLong());
    }

    @Test
    void aRefusedComment_isFailed_butAnUnreachableInstanceStopsTheRound() {
        Incident incident = incident(12L);
        ReflectionTestUtils.setField(incident, "externalId", "s1");
        IncidentComment refused = new IncidentComment(12L, CommentType.COMMENT, "a", 3L);
        ReflectionTestUtils.setField(refused, "id", 40L);
        IncidentComment later = new IncidentComment(12L, CommentType.COMMENT, "b", 3L);
        ReflectionTestUtils.setField(later, "id", 41L);
        when(incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of());
        when(comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING)).thenReturn(List.of(refused, later));
        when(incidents.findById(12L)).thenReturn(Optional.of(incident));
        org.mockito.Mockito.doThrow(serviceNow(Kind.BAD_REQUEST, 400)).when(itsm).pushComment(incident, refused);
        org.mockito.Mockito.doThrow(serviceNow(Kind.UNREACHABLE, 0)).when(itsm).pushComment(incident, later);

        sync.runOnce();

        verify(comments).markStatus(40L, SyncStatus.FAILED);
        verify(comments, never()).markStatus(eq(41L), anyString());
        verify(comments, never()).markSynced(41L);
    }
}
