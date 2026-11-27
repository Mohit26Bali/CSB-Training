package com.icc.incident.entity;

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
@Table(name = "incident_comment")
public class IncidentComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long incidentId;

    @Enumerated(EnumType.STRING)
    private CommentType type;

    @Column(columnDefinition = "TEXT")
    private String body;

    private Long authorId;
    private LocalDateTime createdAt;

    // (sync) Phase 4: a comment is pushed to ServiceNow once its incident is there.
    private String syncStatus;

    protected IncidentComment() {
    }

    public IncidentComment(Long incidentId, CommentType type, String body, Long authorId) {
        this.incidentId = incidentId;
        this.type = type;
        this.body = body;
        this.authorId = authorId;
        this.createdAt = LocalDateTime.now();
        this.syncStatus = SyncStatus.LOCAL_ONLY;
    }

    public Long getId() {
        return id;
    }

    public Long getIncidentId() {
        return incidentId;
    }

    public CommentType getType() {
        return type;
    }

    public String getBody() {
        return body;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public String getSyncStatus() {
        return syncStatus;
    }

    /** When sync is switched on, a new comment waits for the next sync run. */
    public void requestSync() {
        this.syncStatus = SyncStatus.PENDING;
    }
}
