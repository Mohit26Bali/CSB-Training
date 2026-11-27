package com.icc.incident.scheduler;

import com.icc.incident.service.IncidentSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * Runs one sync round every few seconds (icc.itsm.interval-seconds, default 30). It exists only when the sync is switched
 * on (icc.itsm.mode=servicenow); in the default local mode this class is not even created.
 * A failure of a round is logged and never stops the schedule or reaches a user.
 */
@Component
@ConditionalOnProperty(name = "icc.itsm.mode", havingValue = "servicenow")
public class IncidentSyncJob {

    private static final Logger log = LoggerFactory.getLogger(IncidentSyncJob.class);

    private final IncidentSyncService sync;

    public IncidentSyncJob(IncidentSyncService sync) {
        this.sync = sync;
    }

    @Scheduled(fixedDelayString = "${icc.itsm.interval-seconds:30}", initialDelayString = "20", timeUnit = TimeUnit.SECONDS)
    public void run() {
        try {
            IncidentSyncService.Report report = sync.runOnce();
            if (report.didAnything()) {
                log.info("Sync round: {} incident(s) sent, {} refused, {} comment(s) sent{}", report.synced(), report.failed(),
                        report.comments(), report.stoppedEarly() ? ", stopped early because ServiceNow could not be reached" : "");
            }
        } catch (RuntimeException e) {
            log.warn("The sync round failed and will be tried again: {}", e.getMessage());
        }
    }
}
