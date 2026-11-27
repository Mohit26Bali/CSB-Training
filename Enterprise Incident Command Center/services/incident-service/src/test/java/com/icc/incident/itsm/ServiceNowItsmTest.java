package com.icc.incident.itsm;

import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.HoldReason;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.IncidentState;
import com.icc.incident.itsm.ItsmPort.SyncOutcome;
import com.icc.servicenow.ServiceNowClient;
import com.icc.servicenow.ServiceNowException;
import com.icc.servicenow.ServiceNowException.Kind;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The adapter is tested against a mocked ServiceNowClient: no live instance (D9). */
class ServiceNowItsmTest {

    private ServiceNowClient client;
    private ServiceNowItsm itsm;

    @BeforeEach
    void setUp() {
        client = mock(ServiceNowClient.class);
        itsm = new ServiceNowItsm(client);
        when(client.findOne("sys_user", "email", "ananya@icc.com")).thenReturn(Optional.of(Map.of("sys_id", "u-ananya")));
        when(client.findOne("sys_user", "email", "sai@icc.com")).thenReturn(Optional.of(Map.of("sys_id", "u-sai")));
        when(client.findOne("sys_user_group", "name", "Network")).thenReturn(Optional.of(Map.of("sys_id", "g-network")));
        when(client.findByCorrelationId(anyString(), anyString())).thenReturn(Optional.empty());
        when(client.create(eq("incident"), anyMap())).thenReturn(Map.of("sys_id", "s1", "number", "INC0010005", "state", "1"));
        when(client.update(eq("incident"), anyString(), anyMap())).thenReturn(Map.of("sys_id", "s1", "number", "INC0010005", "state", "1"));
    }

    /** Incident 12, reported by Ananya, owned by the Network team, in the given state. */
    private Incident incident(IncidentState state) {
        Incident incident = new Incident("Switch down", "The core switch is down", "Network", 1, 1, 1, 6L, 2L);
        ReflectionTestUtils.setField(incident, "id", 12L);
        incident.setNumber("INC0000012");
        incident.setSubcategory("VPN");
        incident.setReporterEmail("ananya@icc.com");
        incident.setAssignedTeamName("Network");
        incident.setState(state);
        return incident;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> updatesSent() {
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(client, org.mockito.Mockito.atLeast(0)).update(eq("incident"), anyString(), fields.capture());
        return fields.getAllValues();
    }

    // ------------------------------------------------------------------ create

    @Test
    void aNewIncident_isCreated_withOurValuesTranslated() {
        SyncOutcome outcome = itsm.pushIncident(incident(IncidentState.NEW));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> fields = ArgumentCaptor.forClass(Map.class);
        verify(client).create(eq("incident"), fields.capture());
        Map<String, Object> sent = fields.getValue();
        assertEquals("Switch down", sent.get("short_description"));
        assertEquals("network", sent.get("category"));
        assertEquals("vpn", sent.get("subcategory"));
        assertEquals("1", sent.get("impact"));
        assertEquals("1", sent.get("urgency"));
        assertEquals("icc-incident-12", sent.get("correlation_id"));
        assertEquals("u-ananya", sent.get("caller_id"));
        assertEquals("g-network", sent.get("assignment_group"));
        assertEquals("", sent.get("assigned_to"), "nobody is assigned yet: it is said explicitly");
        assertEquals("s1", outcome.externalId());
        assertEquals("INC0010005", outcome.externalNumber());
        assertEquals("icc-incident-12", outcome.correlationId());
        // It is New on both sides, so no state step is needed.
        verify(client, never()).update(anyString(), anyString(), anyMap());
    }

    @Test
    void aRepeatedPush_findsTheRecordByCorrelationId_andNeverCreatesASecondOne() {
        when(client.findByCorrelationId("incident", "icc-incident-12")).thenReturn(Optional.of(Map.of("sys_id", "s9", "number", "INC0010009")));
        when(client.update(eq("incident"), eq("s9"), anyMap())).thenReturn(Map.of("sys_id", "s9", "number", "INC0010009", "state", "1"));

        SyncOutcome outcome = itsm.pushIncident(incident(IncidentState.NEW));

        verify(client, never()).create(anyString(), anyMap());
        verify(client).update(eq("incident"), eq("s9"), anyMap());
        assertEquals("s9", outcome.externalId());
    }

    // ------------------------------------------------------------------ the state, one step at a time

    @Test
    void aResolvedIncidentPushedForTheFirstTime_isCreatedNew_thenMovedToInProgress_thenResolved() {
        Incident incident = incident(IncidentState.RESOLVED);
        incident.setAssignedToEmail("sai@icc.com");
        incident.setResolutionCode("Solution provided");
        incident.setResolutionNotes("Router replaced");

        itsm.pushIncident(incident);

        List<Map<String, Object>> updates = updatesSent();
        assertEquals(2, updates.size());
        assertEquals("2", updates.get(0).get("state"));
        assertEquals("6", updates.get(1).get("state"));
        assertEquals("Solution provided", updates.get(1).get("close_code"));
        assertEquals("Router replaced", updates.get(1).get("close_notes"));
    }

    @Test
    void aClosedIncident_walksThroughInProgressAndResolvedFirst() {
        Incident incident = incident(IncidentState.CLOSED);
        incident.setAssignedToEmail("sai@icc.com");
        incident.setResolutionCode("Workaround provided");
        incident.setResolutionNotes("Cable moved");

        itsm.pushIncident(incident);

        assertEquals(List.of("2", "6", "7"), updatesSent().stream().map(fields -> fields.get("state")).toList());
    }

    @Test
    void anIncidentPutOnHold_sendsTheHoldReason() {
        Incident incident = incident(IncidentState.ON_HOLD);
        ReflectionTestUtils.setField(incident, "externalId", "s1");
        incident.setAssignedToEmail("sai@icc.com");
        ReflectionTestUtils.setField(incident, "holdReason", HoldReason.AWAITING_VENDOR);
        when(client.update(eq("incident"), eq("s1"), anyMap())).thenReturn(Map.of("sys_id", "s1", "number", "INC0010005", "state", "2"));

        itsm.pushIncident(incident);

        List<Map<String, Object>> updates = updatesSent();
        // First the data fields, then the state step with the reason.
        assertEquals(2, updates.size());
        assertEquals("3", updates.get(1).get("state"));
        assertEquals("4", updates.get(1).get("hold_reason"));
    }

    @Test
    void whenServiceNowAlreadyHasTheState_noStateStepIsSent() {
        Incident incident = incident(IncidentState.IN_PROGRESS);
        ReflectionTestUtils.setField(incident, "externalId", "s1");
        incident.setAssignedToEmail("sai@icc.com");
        when(client.update(eq("incident"), eq("s1"), anyMap())).thenReturn(Map.of("sys_id", "s1", "number", "INC0010005", "state", "2"));

        itsm.pushIncident(incident);

        verify(client, times(1)).update(eq("incident"), eq("s1"), anyMap());
    }

    @Test
    void theHopsToEachTarget_followServiceNowsOneStepAtATime() {
        assertEquals(List.of(), ServiceNowItsm.hops("2", "2"));
        assertEquals(List.of("2"), ServiceNowItsm.hops("1", "2"));
        assertEquals(List.of("2"), ServiceNowItsm.hops("7", "2"));
        assertEquals(List.of("2", "3"), ServiceNowItsm.hops("1", "3"));
        assertEquals(List.of("3"), ServiceNowItsm.hops("2", "3"));
        assertEquals(List.of("2", "6"), ServiceNowItsm.hops("1", "6"));
        assertEquals(List.of("2", "6"), ServiceNowItsm.hops("3", "6"));
        assertEquals(List.of("6"), ServiceNowItsm.hops("2", "6"));
        assertEquals(List.of("7"), ServiceNowItsm.hops("6", "7"));
        assertEquals(List.of("6", "7"), ServiceNowItsm.hops("2", "7"));
        assertEquals(List.of("2", "6", "7"), ServiceNowItsm.hops("1", "7"));
        assertEquals(List.of("8"), ServiceNowItsm.hops("2", "8"));
    }

    // ------------------------------------------------------------------ what must not reach ServiceNow

    @Test
    void anOldResolutionCode_isRefusedBeforeAnythingIsCreated() {
        Incident incident = incident(IncidentState.RESOLVED);
        incident.setAssignedToEmail("sai@icc.com");
        incident.setResolutionCode("Fixed");
        incident.setResolutionNotes("x");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> itsm.pushIncident(incident));

        assertTrue(e.getMessage().contains("not a ServiceNow close code"));
        verify(client, never()).create(anyString(), anyMap());
        verify(client, never()).update(anyString(), anyString(), anyMap());
    }

    @Test
    void anIncidentWithoutAReporterEmail_isRefusedWithAClearMessage() {
        Incident incident = incident(IncidentState.NEW);
        incident.setReporterEmail(null);

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> itsm.pushIncident(incident));

        assertTrue(e.getMessage().contains("migrate-incident-sync.sql"));
        verify(client, never()).create(anyString(), anyMap());
    }

    @Test
    void aUserThatServiceNowDoesNotKnow_isANotFoundFailure_andNothingIsCreated() {
        Incident incident = incident(IncidentState.NEW);
        incident.setReporterEmail("stranger@icc.com");
        when(client.findOne("sys_user", "email", "stranger@icc.com")).thenReturn(Optional.empty());

        ServiceNowException e = assertThrows(ServiceNowException.class, () -> itsm.pushIncident(incident));

        assertEquals(Kind.NOT_FOUND, e.kind());
        assertTrue(e.getMessage().contains("create-users-and-groups.js"));
        verify(client, never()).create(anyString(), anyMap());
    }

    @Test
    void aPersonIsLookedUpOnlyOnce() {
        itsm.pushIncident(incident(IncidentState.NEW));
        itsm.pushIncident(incident(IncidentState.NEW));

        verify(client, times(1)).findOne("sys_user", "email", "ananya@icc.com");
    }

    // ------------------------------------------------------------------ comments

    @Test
    void aCommentIsSentAsComments_andAWorkNoteAsWorkNotes() {
        Incident incident = incident(IncidentState.IN_PROGRESS);
        ReflectionTestUtils.setField(incident, "externalId", "s1");

        itsm.pushComment(incident, new IncidentComment(12L, CommentType.COMMENT, "Please send a screenshot", 3L));
        itsm.pushComment(incident, new IncidentComment(12L, CommentType.WORK_NOTE, "Replacing the cable", 3L));

        verify(client).update("incident", "s1", Map.of("comments", "Please send a screenshot"));
        verify(client).update("incident", "s1", Map.of("work_notes", "Replacing the cable"));
        verify(client, never()).create(anyString(), any());
    }
}
