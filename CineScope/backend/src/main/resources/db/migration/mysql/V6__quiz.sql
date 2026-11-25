-- V6 (MySQL): the famous-lines quiz - rounds of ten questions, and what each question was and how it was answered.
--
-- Table <-> entity:  quiz_rounds <-> entity/QuizRound, quiz_questions <-> entity/QuizQuestion; used by service/QuizService. Never edit
-- a script that has been applied; the matching copy for the automated tests is in db/migration/h2.
--
--   * A round belongs to one person and one category (HOLLYWOOD, TV, BOLLYWOOD, ANIME, or ALL for a mix). Its score only counts on
--     the leaderboard once finished_at is set, i.e. after the last question has been answered.
--   * A question stores the four options as one text (separated by |) and which one is right. The correct answer never leaves the
--     server until the question has been answered, so the browser cannot read it ahead of time.
--   * Both tables cascade: deleting an account deletes its rounds, and removing a quote removes the questions that used it.

CREATE TABLE quiz_rounds (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    user_id       BIGINT      NOT NULL,
    category      VARCHAR(12) NOT NULL,
    total         INT         NOT NULL,
    score         INT         NOT NULL,
    correct_count INT         NOT NULL,
    streak        INT         NOT NULL,
    best_streak   INT         NOT NULL,
    started_at    DATETIME(6) NOT NULL,
    finished_at   DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_rounds_board (category, finished_at, score),
    CONSTRAINT fk_rounds_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE quiz_questions (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    round_id      BIGINT       NOT NULL,
    seq           INT          NOT NULL,
    quote_id      BIGINT       NOT NULL,
    kind          VARCHAR(12)  NOT NULL,
    options       VARCHAR(900) NOT NULL,
    correct_index INT          NOT NULL,
    chosen_index  INT          NULL,
    points        INT          NOT NULL,
    answered_at   DATETIME(6)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quiz_question UNIQUE (round_id, seq),
    CONSTRAINT fk_question_round FOREIGN KEY (round_id) REFERENCES quiz_rounds (id) ON DELETE CASCADE,
    CONSTRAINT fk_question_quote FOREIGN KEY (quote_id) REFERENCES quotes (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
