package com.icc.rca.entity;

import jakarta.persistence.Embeddable;

/** The two columns of problem_incident together are its key, so the same pair cannot be stored twice. */
@Embeddable
public record ProblemIncidentKey(Long problemId, Long incidentId) {
}
