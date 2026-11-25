-- V7 (H2): the copy used by the automated tests; the MySQL original (with the explanation) is in db/migration/mysql.
-- Table <-> entity: quiz_questions <-> entity/QuizQuestion.

ALTER TABLE quiz_questions ALTER COLUMN quote_id SET NULL;
ALTER TABLE quiz_questions ADD COLUMN tmdb_id BIGINT;
ALTER TABLE quiz_questions ADD COLUMN media_type VARCHAR(5);
ALTER TABLE quiz_questions ADD COLUMN title VARCHAR(200);
ALTER TABLE quiz_questions ADD COLUMN release_year INT;
ALTER TABLE quiz_questions ADD COLUMN clue VARCHAR(500);
ALTER TABLE quiz_questions ADD COLUMN shown_at TIMESTAMP;
