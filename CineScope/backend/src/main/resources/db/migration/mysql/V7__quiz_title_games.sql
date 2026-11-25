-- V7 (MySQL): two more quiz games, "which film has these actors" (kind CAST) and "name the blurred poster" (kind POSTER).
--
-- Table <-> entity:  quiz_questions <-> entity/QuizQuestion (as in V6). Never edit a script that has been applied; the matching copy for
-- the automated tests is in db/migration/h2.
--
--   * These questions are about a film or show, not about one of the quotes, so quote_id becomes optional and the question keeps what
--     it needs to reveal the answer itself: the TMDB id and type, the title and year, and the clue (the three actors, or the poster
--     picture) that was shown.
--   * shown_at is when the page told the server the question was on screen. The poster game pays a speed bonus measured from it on
--     the server, so the browser's own clock cannot be used to claim one; a question that was never reported shown earns no bonus.

ALTER TABLE quiz_questions
    MODIFY quote_id    BIGINT       NULL,
    ADD COLUMN tmdb_id       BIGINT       NULL,
    ADD COLUMN media_type    VARCHAR(5)   NULL,
    ADD COLUMN title         VARCHAR(200) NULL,
    ADD COLUMN release_year  INT          NULL,
    ADD COLUMN clue          VARCHAR(500) NULL,
    ADD COLUMN shown_at      DATETIME(6)  NULL;
