package com.icc.incident.itsm;

import com.icc.incident.entity.CommentType;
import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.servicenow.ServiceNowClient;
import com.icc.servicenow.ServiceNowException;
import com.icc.servicenow.mapping.CategoryMapping;
import com.icc.servicenow.mapping.ResolutionMapping;
import com.icc.servicenow.mapping.StateMapping;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sends an incident to ServiceNow (D9, D37, D38). All the translation and all the knowledge of the Table API is here
 * and in libs/servicenow-client; the rest of the service does not know it.
 *
 * Pushing an incident does three things:
 *   1. the data fields (text, category, impact, urgency, who reported it, which group, who works on it);
 *   2. create or update: a record that has no ServiceNow id yet is looked up by our correlation id first, so a repeated
 *      push (after a failure) never creates a second record;
 *   3. the state: ServiceNow moves one step at a time, so a RESOLVED incident that is pushed for the first time is created
 *      New, then moved to In Progress, then Resolved (with its close code and notes).
 */
@Component
@ConditionalOnProperty(name = "icc.itsm.mode", havingValue = "servicenow")
public class ServiceNowItsm implements ItsmPort {

    private static final String INCIDENT = "incident";

    private final ServiceNowClient client;
    // People and groups do not change while the service runs, so each is looked up once.
    private final Map<String, String> userIds = new ConcurrentHashMap<>();
    private final Map<String, String> groupIds = new ConcurrentHashMap<>();

    public ServiceNowItsm(ServiceNowClient client) {
        this.client = client;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }

    @Override
    public SyncOutcome pushIncident(Incident incident) {
        String correlationId = "icc-incident-" + incident.getId();
        // Everything that can be wrong in OUR data is checked first, so a bad value never leaves a half-built record in ServiceNow.
        checkSendable(incident);
        Map<String, Object> fields = dataFields(incident, correlationId);

        String sysId = incident.getExternalId();
        Map<String, Object> record;
        if (sysId == null) {
            Optional<Map<String, Object>> existing = client.findByCorrelationId(INCIDENT, correlationId);
            if (existing.isPresent()) {
                // An earlier push created it but did not finish (or its answer was lost): update, never create twice.
                sysId = ServiceNowClient.sysId(existing.get());
                record = client.update(INCIDENT, sysId, fields);
            } else {
                record = client.create(INCIDENT, fields);
                sysId = ServiceNowClient.sysId(record);
            }
        } else {
            record = client.update(INCIDENT, sysId, fields);
        }

        String number = ServiceNowClient.valueOf(record.get("number"));
        if (number == null) {
            number = incident.getExternalNumber();
        }
        String current = ServiceNowClient.valueOf(record.get("state"));
        walkToState(sysId, current == null ? "1" : current, incident);
        return new SyncOutcome(sysId, number, correlationId);
    }

    @Override
    public void pushComment(Incident incident, IncidentComment comment) {
        // A comment the reporter can see is a "comment"; an internal note is a "work note".
        String field = comment.getType() == CommentType.COMMENT ? "comments" : "work_notes";
        client.update(INCIDENT, incident.getExternalId(), Map.of(field, comment.getBody()));
    }

    /** The values the state steps will need: a close code and notes for Resolved or Closed, a reason for On hold. */
    private static void checkSendable(Incident incident) {
        switch (incident.getState()) {
            case RESOLVED, CLOSED -> {
                ResolutionMapping.closeCode(incident.getResolutionCode());
                required(incident.getResolutionNotes(), "resolution notes");
            }
            case ON_HOLD -> required(incident.getHoldReason() == null ? null : incident.getHoldReason().name(), "hold reason");
            default -> { }
        }
    }

    // ---------------------------------------------------------------- the data fields

    private Map<String, Object> dataFields(Incident incident, String correlationId) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("short_description", incident.getShortDescription());
        fields.put("description", incident.getDescription());
        fields.put("category", CategoryMapping.category(incident.getCategory()));
        String subcategory = CategoryMapping.subcategory(incident.getSubcategory());
        if (subcategory != null) {
            fields.put("subcategory", subcategory);
        }
        fields.put("impact", String.valueOf(incident.getImpact()));
        fields.put("urgency", String.valueOf(incident.getUrgency()));
        fields.put("correlation_id", correlationId);
        fields.put("caller_id", userSysId(required(incident.getReporterEmail(), "reporter email")));
        fields.put("assignment_group", groupSysId(required(incident.getAssignedTeamName(), "team name")));
        // Nobody assigned (new, or after a reassign) must also be said, or ServiceNow keeps the old assignee.
        fields.put("assigned_to", incident.getAssignedToEmail() == null ? "" : userSysId(incident.getAssignedToEmail()));
        return fields;
    }

    private static String required(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("This incident has no " + what
                    + " (it is older than the sync): run scripts/migrate-incident-sync.sql to fill it");
        }
        return value;
    }

    private String userSysId(String email) {
        return userIds.computeIfAbsent(email, e -> client.findOne("sys_user", "email", e)
                .map(ServiceNowClient::sysId)
                .orElseThrow(() -> notFound("The user " + e + " does not exist in ServiceNow: run servicenow/create-users-and-groups.js")));
    }

    private String groupSysId(String name) {
        return groupIds.computeIfAbsent(name, n -> client.findOne("sys_user_group", "name", n)
                .map(ServiceNowClient::sysId)
                .orElseThrow(() -> notFound("The group " + n + " does not exist in ServiceNow")));
    }

    private static ServiceNowException notFound(String message) {
        return new ServiceNowException(ServiceNowException.Kind.NOT_FOUND, 404, message, null);
    }

    // ---------------------------------------------------------------- the state

    private void walkToState(String sysId, String current, Incident incident) {
        String target = StateMapping.incidentState(incident.getState().name());
        for (String hop : hops(current, target)) {
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("state", hop);
            if ("3".equals(hop)) {
                fields.put("hold_reason", StateMapping.holdReason(required(incident.getHoldReason() == null ? null : incident.getHoldReason().name(), "hold reason")));
            }
            if ("6".equals(hop)) {
                // The close code is checked here, before the call: ServiceNow answers an unknown one with a confusing HTTP 500.
                fields.put("close_code", ResolutionMapping.closeCode(incident.getResolutionCode()));
                fields.put("close_notes", required(incident.getResolutionNotes(), "resolution notes"));
            }
            client.update(INCIDENT, sysId, fields);
        }
    }

    /** The steps ServiceNow needs to get from the state it is in to the one we want (its state numbers). */
    static List<String> hops(String current, String target) {
        if (target.equals(current)) {
            return List.of();
        }
        return switch (target) {
            case "8" -> List.of("8");
            case "7" -> switch (current) {
                case "6" -> List.of("7");
                case "1", "3" -> List.of("2", "6", "7");
                default -> List.of("6", "7");
            };
            case "6" -> "1".equals(current) || "3".equals(current) ? List.of("2", "6") : List.of("6");
            case "3" -> "1".equals(current) ? List.of("2", "3") : List.of("3");
            default -> List.of(target);
        };
    }
}
