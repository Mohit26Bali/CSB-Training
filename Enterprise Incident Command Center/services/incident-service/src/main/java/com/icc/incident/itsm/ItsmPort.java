package com.icc.incident.itsm;

import com.icc.incident.entity.Incident;
import com.icc.incident.entity.IncidentComment;

/**
 * The door to an IT service management system such as ServiceNow (D9). The business code never knows which system is
 * behind it: there is a local version that does nothing (the default) and a ServiceNow version, chosen by the setting
 * icc.itsm.mode. Business code never imports ServiceNow classes (hard rule 6).
 */
public interface ItsmPort {

    /** False for the local version: nothing is sent anywhere. */
    boolean isEnabled();

    /**
     * Makes the other system hold the current version of the incident: creates it, or updates it, and walks its state
     * to ours. Safe to repeat: before it creates a record it asks whether one with our correlation id already exists.
     */
    SyncOutcome pushIncident(Incident incident);

    /** Adds one comment or work note to the incident that already exists on the other side. */
    void pushComment(Incident incident, IncidentComment comment);

    /** What the other system answered: its id and number for the record, and the correlation id that links the two. */
    record SyncOutcome(String externalId, String externalNumber, String correlationId) {
    }
}
