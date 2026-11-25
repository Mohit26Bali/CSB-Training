package com.csb.cinescope.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One play of the quiz: ten questions in one category for one person (table {@code quiz_rounds}, migration V6). Created and
 * updated by {@code service/QuizService}. The score is the sum of the points of the answered questions; the round counts on the
 * leaderboard only once {@code finishedAt} is set. Holds the person's id rather than a link, as the database cascades deletes.
 */
@Entity
@Table(name = "quiz_rounds")
public class QuizRound {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private long userId;

    /** HOLLYWOOD, TV, BOLLYWOOD, ANIME, or ALL for a mix. */
    @Column(nullable = false, length = 12)
    private String category;

    @Column(nullable = false)
    private int total;

    @Column(nullable = false)
    private int score;

    @Column(name = "correct_count", nullable = false)
    private int correctCount;

    /** Right answers in a row so far. */
    @Column(nullable = false)
    private int streak;

    @Column(name = "best_streak", nullable = false)
    private int bestStreak;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    protected QuizRound() {
        // required by JPA
    }

    public QuizRound(long userId, String category, int total) {
        this.userId = userId;
        this.category = category;
        this.total = total;
        this.startedAt = LocalDateTime.now();
    }

    /** Records one answer: the points, and the streak that follows from it. */
    public void record(boolean correct, int points) {
        score += points;
        if (correct) {
            correctCount++;
            streak++;
            bestStreak = Math.max(bestStreak, streak);
        } else {
            streak = 0;
        }
    }

    public void finish() {
        this.finishedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public long getUserId() {
        return userId;
    }

    public String getCategory() {
        return category;
    }

    public int getTotal() {
        return total;
    }

    public int getScore() {
        return score;
    }

    public int getCorrectCount() {
        return correctCount;
    }

    public int getStreak() {
        return streak;
    }

    public int getBestStreak() {
        return bestStreak;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }
}
