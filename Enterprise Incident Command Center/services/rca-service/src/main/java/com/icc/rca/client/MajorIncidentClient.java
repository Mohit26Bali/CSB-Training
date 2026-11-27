package com.icc.rca.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/** The RCA service's view of the Major Incident service (ARCHITECTURE: RCA calls Major Incident, one way). */
@FeignClient(name = "major-incident-service")
public interface MajorIncidentClient {

    @GetMapping("/internal/major-incidents/{id}")
    MajorIncidentDto get(@PathVariable("id") Long id);

    record MajorIncidentDto(Long id, Long incidentId, String summary, String status) {
    }
}
