package com.springlab.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A single-answer multiple-choice question. Never hard-deleted: teachers deactivate it so attempt history stays valid. */
@Entity
@Table(name = "questions")
@EntityListeners(AuditingEntityListener.class)
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "topic_id", nullable = false)
    private Topic topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Difficulty difficulty;

    @Column(name = "question_text", nullable = false, length = 2000)
    private String text;

    @Column(nullable = false, length = 4000)
    private String explanation;

    @Column(nullable = false)
    private boolean active = true;

    /** Null for questions authored by SpringLab itself (seed data). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Optimistic locking: a concurrent edit makes the second save fail instead of silently overwriting. */
    @Version
    private Long version;

    @OneToMany(mappedBy = "question", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<QuestionOption> options = new ArrayList<>();

    protected Question() {
        // required by JPA
    }

    public Question(Topic topic, Difficulty difficulty, String text, String explanation, User createdBy) {
        this.topic = topic;
        this.difficulty = difficulty;
        this.text = text;
        this.explanation = explanation;
        this.createdBy = createdBy;
    }

    public Long getId() { return id; }
    public Topic getTopic() { return topic; }
    public Difficulty getDifficulty() { return difficulty; }
    public String getText() { return text; }
    public String getExplanation() { return explanation; }
    public boolean isActive() { return active; }
    public User getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Long getVersion() { return version; }
    public List<QuestionOption> getOptions() { return options; }

    public void setActive(boolean active) { this.active = active; }

    /**
     * Marks the question itself as modified. Changes confined to the options collection (the inverse, mappedBy side)
     * do not dirty this row, so Hibernate would neither bump {@code version} nor refresh {@code updatedAt};
     * option-only edits would then slip past optimistic locking.
     */
    public void touch() {
        this.updatedAt = Instant.now();
    }

    public void updateContent(Topic topic, Difficulty difficulty, String text, String explanation) {
        this.topic = topic;
        this.difficulty = difficulty;
        this.text = text;
        this.explanation = explanation;
    }

    /** Adds a new option and keeps both sides of the association in sync. */
    public QuestionOption addOption(String text, boolean correct, int position) {
        QuestionOption option = new QuestionOption(this, text, correct, position);
        options.add(option);
        return option;
    }

    /** Removes persisted options whose ids are not listed; orphanRemoval = true deletes the rows on flush. */
    public void removeOptionsNotIn(Set<Long> keepIds) {
        options.removeIf(option -> option.getId() != null && !keepIds.contains(option.getId()));
    }

    public boolean isOwnedBy(User user) {
        return createdBy != null && user != null && Objects.equals(createdBy.getId(), user.getId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Question other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
