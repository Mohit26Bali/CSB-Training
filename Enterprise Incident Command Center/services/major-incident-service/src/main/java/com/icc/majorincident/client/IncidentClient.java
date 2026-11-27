package com.icc.majorincident.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * The Major Incident service's view of the Incident service (ARCHITECTURE: Major Incident calls Incident, one way).
 * The caller's token is added by common-lib's Feign interceptor.
 */
@FeignClient(name = "incident-service")
public interface IncidentClient {

    @GetMapping("/internal/incidents/{id}")
    IncidentDto get(@PathVariable("id") Long id);

    @PutMapping("/internal/incidents/{id}/major-incident-link")
    void linkMajorIncident(@PathVariable("id") Long id, @RequestBody LinkRequest request);

    record IncidentDto(Long id, String number, String shortDescription, int priority, String state,
                       Long majorIncidentId) {
    }

    record LinkRequest(Long majorIncidentId) {
    }
}
