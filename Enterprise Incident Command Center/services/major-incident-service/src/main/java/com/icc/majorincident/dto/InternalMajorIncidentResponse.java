package com.icc.majorincident.dto;

/** What other services (RCA) may know about a major incident. The status is plain text. */
public record InternalMajorIncidentResponse(Long id, Long incidentId, String summary, String status) {
}
