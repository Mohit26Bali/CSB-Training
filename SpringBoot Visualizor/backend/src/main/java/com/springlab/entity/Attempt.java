package com.springlab.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** A student's run through a set of questions. It can be submitted exactly once. */
@Entity
@Table(name = "attempts")
public class Attempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Null = questions drawn from all topics. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    /** Null = any difficulty. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private Difficulty difficulty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private AttemptStatus status = AttemptStatus.IN_PROGRESS;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Column(name = "total_questions", nullable = false)
    private int totalQuestions;

    /** Number of correct answers; null until submitted. */
    private Integer score;

    @OneToMany(mappedBy = "attempt", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<AttemptAnswer> answers = new ArrayList<>();

    protected Attempt() {
        // required by JPA
    }

    public Attempt(User user, Topic topic, Difficulty difficulty, List<Question> questions) {
        this.user = user;
        this.topic = topic;
        this.difficulty = difficulty;
        this.totalQuestions = questions.size();
        this.startedAt = Instant.now();
        for (int i = 0; i < questions.size(); i++) {
            answers.add(new AttemptAnswer(this, questions.get(i), i));
        }
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public Topic getTopic() { return topic; }
    public Difficulty getDifficulty() { return difficulty; }
    public AttemptStatus getStatus() { return status; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getSubmittedAt() { return submittedAt; }
    public int getTotalQuestions() { return totalQuestions; }
    public Integer getScore() { return score; }
    public List<AttemptAnswer> getAnswers() { return answers; }

    /**
     * Records the chosen options (keyed by question id; absent = skipped), computes the score and closes the attempt.
     * The caller must have validated the input and hold a lock on this attempt.
     */
    public void submit(Map<Long, QuestionOption> selectedByQuestionId) {
        if (status != AttemptStatus.IN_PROGRESS) {
            throw new IllegalStateException("Attempt already submitted");
        }
        int correctCount = 0;
        for (AttemptAnswer answer : answers) {
            answer.record(selectedByQuestionId.get(answer.getQuestion().getId()));
            if (Boolean.TRUE.equals(answer.getCorrect())) {
                correctCount++;
            }
        }
        this.score = correctCount;
        this.status = AttemptStatus.SUBMITTED;
        this.submittedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Attempt other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
