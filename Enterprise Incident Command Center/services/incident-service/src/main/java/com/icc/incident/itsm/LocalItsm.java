package com.icc.incident.itsm;

import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** The default: sync is off. Nothing is sent, and the sync job does not exist in this mode. */
@Component
@ConditionalOnProperty(name = "icc.itsm.mode", havingValue = "local", matchIfMissing = true)
public class LocalItsm implements ItsmPort {

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public SyncOutcome pushIncident(Incident incident) {
        throw new IllegalStateException("The sync to ServiceNow is switched off (icc.itsm.mode=local)");
    }

    @Override
    public void pushComment(Incident incident, IncidentComment comment) {
        throw new IllegalStateException("The sync to ServiceNow is switched off (icc.itsm.mode=local)");
    }
}
