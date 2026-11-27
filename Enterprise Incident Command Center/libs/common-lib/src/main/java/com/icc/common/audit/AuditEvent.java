package com.icc.common.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * One row of the audit trail. Every service that has a database has its own audit_event table
 * with these columns (DATA-MODEL, D11). The class is shared; the table is not.
 */
@Entity
@Table(name = "audit_event")
public class AuditEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long actorId;
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
    private String correlationId;

    @Column(insertable = false, updatable = false)
    private LocalDateTime createdAt;

    protected AuditEvent() {
    }

    public AuditEvent(Long actorId, String action, String entityType, Long entityId,
                      String details, String correlationId) {
        this.actorId = actorId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.details = details;
        this.correlationId = correlationId;
    }

    public Long getId() {
        return id;
    }

    public Long getActorId() {
        return actorId;
    }

    public String getAction() {
        return action;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public String getDetails() {
        return details;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
