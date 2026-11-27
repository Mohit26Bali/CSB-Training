package com.icc.incident.entity;

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
@Table(name = "incident")
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String number;
    private String shortDescription;

    @Column(columnDefinition = "TEXT")
    private String description;

    private String category;

    // ServiceNow's subcategory: optional, and it must belong to the category.
    private String subcategory;

    private int impact;
    private int urgency;
    private int priority;

    @Enumerated(EnumType.STRING)
    private IncidentState state;

    // Other services' records are referred to by id only (hard rule 4).
    private Long reporterId;
    private Long assignedTeamId;
    private Long assignedToId;

    // Set only while the state is ON_HOLD.
    @Enumerated(EnumType.STRING)
    private HoldReason holdReason;

    private String resolutionCode;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    private Long majorIncidentId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    // (sync) Phase 4 (D37, D38). Changed only by the sync job, apart from requestSync() in the service.
    private String externalId;
    private String externalNumber;
    private String syncStatus;
    private LocalDateTime lastSyncedAt;
    private String correlationId;
    private String lastSyncError;

    // A copy of what the sync needs from other services, filled while the user's own request runs (D38): the job runs
    // without a logged-in user and so cannot ask the Assignment service. The login name is the email (D35).
    private String reporterEmail;
    private String assignedToEmail;
    private String assignedTeamName;

    protected Incident() {
    }

    public Incident(String shortDescription, String description, String category,
                    int impact, int urgency, int priority, Long reporterId, Long assignedTeamId) {
        this.shortDescription = shortDescription;
        this.description = description;
        this.category = category;
        this.impact = impact;
        this.urgency = urgency;
        this.priority = priority;
        this.state = IncidentState.NEW;
        this.reporterId = reporterId;
        this.assignedTeamId = assignedTeamId;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.syncStatus = SyncStatus.LOCAL_ONLY;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
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

    public String getShortDescription() {
        return shortDescription;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubcategory() {
        return subcategory;
    }

    public void setSubcategory(String subcategory) {
        this.subcategory = subcategory;
    }

    public HoldReason getHoldReason() {
        return holdReason;
    }

    public void setHoldReason(HoldReason holdReason) {
        this.holdReason = holdReason;
    }

    public int getImpact() {
        return impact;
    }

    public void setImpact(int impact) {
        this.impact = impact;
    }

    public int getUrgency() {
        return urgency;
    }

    public void setUrgency(int urgency) {
        this.urgency = urgency;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public IncidentState getState() {
        return state;
    }

    public void setState(IncidentState state) {
        this.state = state;
    }

    public Long getReporterId() {
        return reporterId;
    }

    public Long getAssignedTeamId() {
        return assignedTeamId;
    }

    public void setAssignedTeamId(Long assignedTeamId) {
        this.assignedTeamId = assignedTeamId;
    }

    public Long getAssignedToId() {
        return assignedToId;
    }

    public void setAssignedToId(Long assignedToId) {
        this.assignedToId = assignedToId;
    }

    public String getResolutionCode() {
        return resolutionCode;
    }

    public void setResolutionCode(String resolutionCode) {
        this.resolutionCode = resolutionCode;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String resolutionNotes) {
        this.resolutionNotes = resolutionNotes;
    }

    public Long getMajorIncidentId() {
        return majorIncidentId;
    }

    public void setMajorIncidentId(Long majorIncidentId) {
        this.majorIncidentId = majorIncidentId;
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

    /** The record changed: when sync is switched on, it waits for the next sync run. */
    public void requestSync() {
        this.syncStatus = SyncStatus.PENDING;
        this.lastSyncError = null;
    }

    public String getExternalId() {
        return externalId;
    }

    public String getExternalNumber() {
        return externalNumber;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }

    public String getReporterEmail() {
        return reporterEmail;
    }

    public void setReporterEmail(String reporterEmail) {
        this.reporterEmail = reporterEmail;
    }

    public String getAssignedToEmail() {
        return assignedToEmail;
    }

    public void setAssignedToEmail(String assignedToEmail) {
        this.assignedToEmail = assignedToEmail;
    }

    public String getAssignedTeamName() {
        return assignedTeamName;
    }

    public void setAssignedTeamName(String assignedTeamName) {
        this.assignedTeamName = assignedTeamName;
    }
}
