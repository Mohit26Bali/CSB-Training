package com.icc.incident.dto;

/** What other services may know about an incident. The state is plain text, so the other service needs no enum. */
public record InternalIncidentResponse(Long id, String number, String shortDescription,
                                       int priority, String state, Long majorIncidentId) {
}
