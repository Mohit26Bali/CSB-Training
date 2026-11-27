package com.icc.rca.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/** The RCA service's view of the Incident service (ARCHITECTURE: RCA calls Incident, one way). */
@FeignClient(name = "incident-service")
public interface IncidentClient {

    @GetMapping("/internal/incidents/{id}")
    IncidentDto get(@PathVariable("id") Long id);

    /** Tells an incident that a problem covering it was closed: an incident on hold goes back to work (D32). */
    @PostMapping("/internal/incidents/{id}/problem-closed")
    IncidentDto problemClosed(@PathVariable("id") Long id, @RequestBody ProblemClosedBody body);

    record IncidentDto(Long id, String number, String shortDescription, int priority, String state,
                       Long majorIncidentId) {
    }

    record ProblemClosedBody(String problemNumber, String closeNotes) {
    }
}
