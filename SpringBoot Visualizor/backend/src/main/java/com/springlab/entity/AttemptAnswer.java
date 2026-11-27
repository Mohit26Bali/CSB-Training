package com.springlab.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** One question of an attempt. Created when the attempt starts; the answer is filled in on submission. */
@Entity
@Table(name = "attempt_answers")
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "attempt_id", nullable = false)
    private Attempt attempt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "sort_order", nullable = false)
    private int position;

    /** Null when skipped, or when a teacher later removed the option (database ON DELETE SET NULL). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_option_id")
    private QuestionOption selectedOption;

    /** Snapshot taken at submission time; never recomputed if the question is edited afterwards. */
    private Boolean correct;

    protected AttemptAnswer() {
        // required by JPA
    }

    AttemptAnswer(Attempt attempt, Question question, int position) {
        this.attempt = attempt;
        this.question = question;
        this.position = position;
    }

    public Long getId() { return id; }
    public Attempt getAttempt() { return attempt; }
    public Question getQuestion() { return question; }
    public int getPosition() { return position; }
    public QuestionOption getSelectedOption() { return selectedOption; }
    public Boolean getCorrect() { return correct; }

    void record(QuestionOption selected) {
        this.selectedOption = selected;
        this.correct = selected != null && selected.isCorrect();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AttemptAnswer other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
