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

@Entity
@Table(name = "question_options")
public class QuestionOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Owning side of the Question 1-N QuestionOption association (holds the question_id foreign key). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "question_id", nullable = false)
    private Question question;

    @Column(name = "option_text", nullable = false, length = 500)
    private String text;

    @Column(nullable = false)
    private boolean correct;

    @Column(name = "sort_order", nullable = false)
    private int position;

    protected QuestionOption() {
        // required by JPA
    }

    QuestionOption(Question question, String text, boolean correct, int position) {
        this.question = question;
        this.text = text;
        this.correct = correct;
        this.position = position;
    }

    public Long getId() { return id; }
    public Question getQuestion() { return question; }
    public String getText() { return text; }
    public boolean isCorrect() { return correct; }
    public int getPosition() { return position; }

    public void update(String text, boolean correct, int position) {
        this.text = text;
        this.correct = correct;
        this.position = position;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QuestionOption other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
