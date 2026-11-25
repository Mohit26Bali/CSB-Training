package com.csb.cinescope.entity;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One question of a {@link QuizRound} (table {@code quiz_questions}, migrations V6 and V7), with four options and the index of the right
 * one. Its kind says what is asked: {@code TITLE} (which film or show is this quote from) and {@code SPEAKER} (who says it) are about a
 * quote; {@code CAST} (which title has these actors) and {@code POSTER} (name the blurred poster) are about a film or show, so they
 * carry their own title, year and clue instead of a quote. The correct index is read only by {@code service/QuizService}, which reveals
 * it after the question has been answered.
 */
@Entity
@Table(name = "quiz_questions")
public class QuizQuestion {

    /** The options are stored in one column, separated by this; no title or speaker in the quotes file contains it. */
    static final String SEPARATOR = "|";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "round_id", nullable = false)
    private long roundId;

    @Column(nullable = false)
    private int seq;

    /** The quote this question is about; null for the CAST and POSTER kinds. */
    @Column(name = "quote_id")
    private Long quoteId;

    @Column(nullable = false, length = 12)
    private String kind;

    @Column(nullable = false, length = 900)
    private String options;

    @Column(name = "correct_index", nullable = false)
    private int correctIndex;

    @Column(name = "chosen_index")
    private Integer chosenIndex;

    @Column(nullable = false)
    private int points;

    @Column(name = "answered_at")
    private LocalDateTime answeredAt;

    // ---- only for the CAST and POSTER kinds (migration V7) ----

    @Column(name = "tmdb_id")
    private Long tmdbId;

    @Column(name = "media_type", length = 5)
    private String mediaType;

    @Column(length = 200)
    private String title;

    @Column(name = "release_year")
    private Integer releaseYear;

    /** CAST: the actors shown, separated by the same separator as the options. POSTER: the poster's TMDB image path. */
    @Column(length = 500)
    private String clue;

    /** When the page reported that the question was on screen; null until it does. The speed bonus is measured from it. */
    @Column(name = "shown_at")
    private LocalDateTime shownAt;

    protected QuizQuestion() {
        // required by JPA
    }

    public QuizQuestion(long roundId, int seq, long quoteId, String kind, List<String> options, int correctIndex) {
        this.roundId = roundId;
        this.seq = seq;
        this.quoteId = quoteId;
        this.kind = kind;
        this.options = String.join(SEPARATOR, options);
        this.correctIndex = correctIndex;
    }

    /** A question about a film or show rather than a quote (kinds CAST and POSTER). */
    public QuizQuestion(long roundId, int seq, String kind, long tmdbId, String mediaType, String title, int releaseYear, String clue, List<String> options, int correctIndex) {
        this.roundId = roundId;
        this.seq = seq;
        this.kind = kind;
        this.tmdbId = tmdbId;
        this.mediaType = mediaType;
        this.title = title;
        this.releaseYear = releaseYear;
        this.clue = clue;
        this.options = String.join(SEPARATOR, options);
        this.correctIndex = correctIndex;
    }

    /** Records when the question reached the screen. Only the first report counts: asking again cannot restart the clock. */
    public void markShown(LocalDateTime when) {
        if (shownAt == null) {
            shownAt = when;
        }
    }

    public void answer(int chosen, int earned) {
        this.chosenIndex = chosen;
        this.points = earned;
        this.answeredAt = LocalDateTime.now();
    }

    public boolean isAnswered() {
        return answeredAt != null;
    }

    public List<String> getOptions() {
        return Arrays.asList(options.split("\\" + SEPARATOR, -1));
    }

    public Long getId() {
        return id;
    }

    public long getRoundId() {
        return roundId;
    }

    public int getSeq() {
        return seq;
    }

    public Long getQuoteId() {
        return quoteId;
    }

    public Long getTmdbId() {
        return tmdbId;
    }

    public String getMediaType() {
        return mediaType;
    }

    public String getTitle() {
        return title;
    }

    public Integer getReleaseYear() {
        return releaseYear;
    }

    public String getClue() {
        return clue;
    }

    public LocalDateTime getShownAt() {
        return shownAt;
    }

    public String getKind() {
        return kind;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public Integer getChosenIndex() {
        return chosenIndex;
    }

    public int getPoints() {
        return points;
    }
}
