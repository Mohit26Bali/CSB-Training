package com.icc.rca.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Link table: which incidents a problem covers. */
@Entity
@Table(name = "problem_incident")
public class ProblemIncident {

    @EmbeddedId
    private ProblemIncidentKey key;

    protected ProblemIncident() {
    }

    public ProblemIncident(Long problemId, Long incidentId) {
        this.key = new ProblemIncidentKey(problemId, incidentId);
    }

    public ProblemIncidentKey getKey() {
        return key;
    }
}
