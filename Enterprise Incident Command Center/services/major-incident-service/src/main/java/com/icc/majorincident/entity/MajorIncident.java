package com.icc.majorincident.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "major_incident")
public class MajorIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The incident is a record of the Incident service: referred to by id only (hard rule 4).
    private Long incidentId;

    private String summary;

    @Column(columnDefinition = "TEXT")
    private String impactSummary;

    private Long commanderId;

    @Enumerated(EnumType.STRING)
    private MajorIncidentStatus status;

    private Long declaredById;
    private LocalDateTime declaredAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    // (sync) Phase 4. Read only for now: the database fills LOCAL_ONLY.
    @Column(insertable = false, updatable = false)
    private String syncStatus;

    protected MajorIncident() {
    }

    public MajorIncident(Long incidentId, String summary, String impactSummary,
                         Long commanderId, Long declaredById) {
        this.incidentId = incidentId;
        this.summary = summary;
        this.impactSummary = impactSummary;
        this.commanderId = commanderId;
        this.declaredById = declaredById;
        this.status = MajorIncidentStatus.DECLARED;
        this.declaredAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public String getSummary() {
        return summary;
    }

    public String getImpactSummary() {
        return impactSummary;
    }

    public Long getCommanderId() {
        return commanderId;
    }

    public MajorIncidentStatus getStatus() {
        return status;
    }

    public void setStatus(MajorIncidentStatus status) {
        this.status = status;
    }

    public Long getDeclaredById() {
        return declaredById;
    }

    public LocalDateTime getDeclaredAt() {
        return declaredAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(LocalDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }
}
