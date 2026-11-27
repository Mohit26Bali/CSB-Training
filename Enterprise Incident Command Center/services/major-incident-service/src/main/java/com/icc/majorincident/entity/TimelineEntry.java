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
@Table(name = "timeline_entry")
public class TimelineEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long majorIncidentId;

    @Enumerated(EnumType.STRING)
    private TimelineEntryType entryType;

    @Column(columnDefinition = "TEXT")
    private String text;

    private Long authorId;
    private LocalDateTime createdAt;

    protected TimelineEntry() {
    }

    public TimelineEntry(Long majorIncidentId, TimelineEntryType entryType, String text, Long authorId) {
        this.majorIncidentId = majorIncidentId;
        this.entryType = entryType;
        this.text = text;
        this.authorId = authorId;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getMajorIncidentId() {
        return majorIncidentId;
    }

    public TimelineEntryType getEntryType() {
        return entryType;
    }

    public String getText() {
        return text;
    }

    public Long getAuthorId() {
        return authorId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
