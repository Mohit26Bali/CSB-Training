package com.icc.incident.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * The Incident service's view of the Assignment service (ARCHITECTURE: Incident calls Assignment, one way).
 * The name is the Eureka service name. The caller's token is added by common-lib's Feign interceptor.
 */
@FeignClient(name = "assignment-service")
public interface AssignmentClient {

    @GetMapping("/internal/assignment/resolve")
    ResolveResponse resolve(@RequestParam("category") String category);

    @GetMapping("/api/teams")
    List<TeamDto> teams();

    record ResolveResponse(Long teamId) {
    }

    record TeamDto(Long id, String name, String description) {
    }
}
