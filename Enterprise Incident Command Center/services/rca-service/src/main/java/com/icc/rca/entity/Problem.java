package com.icc.rca.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "problem")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    private ProblemStatus status;

    // ServiceNow's cause_notes (written when the Fix step is pressed, or edited before).
    @Column(columnDefinition = "TEXT")
    private String rootCause;

    private String rootCauseCategory;

    @Column(columnDefinition = "TEXT")
    private String workaround;

    // ServiceNow's fix_notes.
    @Column(columnDefinition = "TEXT")
    private String fixNotes;

    // Set by the action that ends the problem, never typed.
    @Enumerated(EnumType.STRING)
    private ResolutionCode resolutionCode;

    // Another problem of this service (same schema), set by MARK_DUPLICATE.
    private Long duplicateOfId;

    // Only meaningful for ACCEPT_RISK: was the workaround put in place?
    private boolean workaroundApplied;

    // A user of the Assignment service, by id only (hard rule 4).
    private Long assignedToId;

    // 1 High to 3 Low, as on an incident. Priority is calculated, never typed (D31).
    private int impact;
    private int urgency;
    private int priority;

    // Refers to a record of the Major Incident service by id only (hard rule 4).
    private Long majorIncidentId;

    private int reopenCount;

    private Long createdById;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    // (sync) Phase 4. Read only for now: the database fills LOCAL_ONLY.
    @Column(insertable = false, updatable = false)
    private String syncStatus;

    protected Problem() {
    }

    public Problem(String title, String description, Long majorIncidentId, Long createdById) {
        this.title = title;
        this.description = description;
        this.majorIncidentId = majorIncidentId;
        this.createdById = createdById;
        this.status = ProblemStatus.NEW;
        setImpactAndUrgency(3, 3);
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    /** Same formula as an incident: every cell of the table is impact + urgency - 1 (1 Critical to 5 Planning). */
    public final void setImpactAndUrgency(int impact, int urgency) {
        this.impact = impact;
        this.urgency = urgency;
        this.priority = impact + urgency - 1;
    }

    public boolean hasRootCause() {
        return rootCause != null && !rootCause.isBlank();
    }

    public boolean hasFixNotes() {
        return fixNotes != null && !fixNotes.isBlank();
    }

    public boolean hasWorkaround() {
        return workaround != null && !workaround.isBlank();
    }

    public Long getId() {
        return id;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ProblemStatus getStatus() {
        return status;
    }

    public void setStatus(ProblemStatus status) {
        this.status = status;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public String getRootCauseCategory() {
        return rootCauseCategory;
    }

    public void setRootCauseCategory(String rootCauseCategory) {
        this.rootCauseCategory = rootCauseCategory;
    }

    public String getWorkaround() {
        return workaround;
    }

    public void setWorkaround(String workaround) {
        this.workaround = workaround;
    }

    public String getFixNotes() {
        return fixNotes;
    }

    public void setFixNotes(String fixNotes) {
        this.fixNotes = fixNotes;
    }

    public ResolutionCode getResolutionCode() {
        return resolutionCode;
    }

    public void setResolutionCode(ResolutionCode resolutionCode) {
        this.resolutionCode = resolutionCode;
    }

    public Long getDuplicateOfId() {
        return duplicateOfId;
    }

    public void setDuplicateOfId(Long duplicateOfId) {
        this.duplicateOfId = duplicateOfId;
    }

    public boolean isWorkaroundApplied() {
        return workaroundApplied;
    }

    public void setWorkaroundApplied(boolean workaroundApplied) {
        this.workaroundApplied = workaroundApplied;
    }

    public Long getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(Long assignedToId) {
        this.assignedToId = assignedToId;
    }

    public int getImpact() {
        return impact;
    }

    public int getUrgency() {
        return urgency;
    }

    public int getPriority() {
        return priority;
    }

    public Long getMajorIncidentId() {
        return majorIncidentId;
    }

    public int getReopenCount() {
        return reopenCount;
    }

    public void countReopen() {
        this.reopenCount++;
    }

    public Long getCreatedById() {
        return createdById;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
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
