package com.icc.incident.service;

import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import com.icc.incident.entity.SyncStatus;
import com.icc.incident.itsm.ItsmPort;
import com.icc.incident.itsm.ItsmPort.SyncOutcome;
import com.icc.incident.repository.IncidentCommentRepository;
import com.icc.incident.repository.IncidentRepository;
import com.icc.servicenow.ServiceNowException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One round of the sync to ServiceNow (D9, D38): pushes the incidents that wait (PENDING), then the comments that wait.
 * The scheduler calls runOnce() every few seconds; nothing here is called inside a user's request.
 *
 * What happens to one incident:
 *   - it works: ServiceNow's id and number are stored, and the incident becomes SYNCED, but only if nobody changed it
 *     while ServiceNow was being called (otherwise it stays PENDING and goes again);
 *   - ServiceNow cannot be reached, or has a server error: the incident stays PENDING with the reason, and the round
 *     stops (a dead instance is not asked twenty times);
 *   - ServiceNow refuses it, or it cannot be sent (a wrong value, a missing user): FAILED with the reason. It is not
 *     retried until the incident changes again, because the same call would fail the same way.
 */
@Service
public class IncidentSyncService {

    private static final Logger log = LoggerFactory.getLogger(IncidentSyncService.class);
    private static final int MAX_ERROR = 500;

    public record Report(int synced, int failed, int comments, boolean stoppedEarly) {
        public boolean didAnything() {
            return synced > 0 || failed > 0 || comments > 0 || stoppedEarly;
        }
    }

    private enum Result { SYNCED, FAILED, RETRY_LATER }

    private final IncidentRepository incidents;
    private final IncidentCommentRepository comments;
    private final ItsmPort itsm;

    public IncidentSyncService(IncidentRepository incidents, IncidentCommentRepository comments, ItsmPort itsm) {
        this.incidents = incidents;
        this.comments = comments;
        this.itsm = itsm;
    }

    public Report runOnce() {
        int synced = 0;
        int failed = 0;
        boolean stopped = false;

        for (Incident incident : incidents.findTop20BySyncStatusOrderByIdAsc(SyncStatus.PENDING)) {
            Result result = syncOne(incident);
            if (result == Result.SYNCED) {
                synced++;
            } else if (result == Result.FAILED) {
                failed++;
            } else {
                stopped = true;
                break;
            }
        }

        int sentComments = stopped ? 0 : syncComments();
        return new Report(synced, failed, sentComments, stopped);
    }

    private Result syncOne(Incident incident) {
        Long id = incident.getId();
        // The version we read. The status is written back only while the incident still has this updatedAt.
        LocalDateTime readAt = incident.getUpdatedAt();
        try {
            SyncOutcome outcome = itsm.pushIncident(incident);
            incidents.saveExternalIds(id, outcome.externalId(), outcome.externalNumber(), outcome.correlationId());
            incidents.markSynced(id, readAt, LocalDateTime.now());
            log.info("Incident {} is in ServiceNow as {}", incident.getNumber(), outcome.externalNumber());
            return Result.SYNCED;
        } catch (ServiceNowException e) {
            if (e.isWorthRetrying()) {
                log.warn("Incident {} could not be sent now ({}); it stays PENDING", incident.getNumber(), e.kind());
                incidents.markSyncResult(id, readAt, SyncStatus.PENDING, shorten(e.getMessage()));
                return Result.RETRY_LATER;
            }
            log.warn("Incident {} was refused by ServiceNow ({}): {}", incident.getNumber(), e.kind(), e.getMessage());
            incidents.markSyncResult(id, readAt, SyncStatus.FAILED, shorten(e.getMessage()));
            return Result.FAILED;
        } catch (IllegalArgumentException | IllegalStateException e) {
            // Something in our own data cannot be sent (a close code that is not one of the ten, a missing email).
            log.warn("Incident {} cannot be sent: {}", incident.getNumber(), e.getMessage());
            incidents.markSyncResult(id, readAt, SyncStatus.FAILED, shorten(e.getMessage()));
            return Result.FAILED;
        }
    }

    private int syncComments() {
        int sent = 0;
        List<IncidentComment> waiting = comments.findTop50BySyncStatusOrderByIdAsc(SyncStatus.PENDING);
        for (IncidentComment comment : waiting) {
            Incident incident = incidents.findById(comment.getIncidentId()).orElse(null);
            if (incident == null || incident.getExternalId() == null) {
                continue; // its incident has to reach ServiceNow first
            }
            try {
                itsm.pushComment(incident, comment);
                comments.markSynced(comment.getId());
                sent++;
            } catch (ServiceNowException e) {
                log.warn("A comment of {} could not be sent ({}): {}", incident.getNumber(), e.kind(), e.getMessage());
                if (e.isWorthRetrying()) {
                    break;
                }
                comments.markStatus(comment.getId(), SyncStatus.FAILED);
            }
        }
        return sent;
    }

    private static String shorten(String text) {
        if (text == null) {
            return null;
        }
        return text.length() <= MAX_ERROR ? text : text.substring(0, MAX_ERROR);
    }
}
