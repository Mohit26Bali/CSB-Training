-- V4 (MySQL): famous lines for the sign-in screens and the quiz.
--
-- Table <-> entity:  quotes <-> entity/Quote. Filled once by config/QuoteSeeder from resources/seed/quotes.psv (only while the
-- table is empty, so edits made later are kept). Never edit a script that has been applied; the matching copy for the automated
-- tests is in db/migration/h2.
--
--   * category: HOLLYWOOD, TV, BOLLYWOOD or ANIME. media_type + tmdb_id is the same title identity the catalogue uses, so the
--     poster and cast come from TMDB; the line and the speaker are written by us because TMDB has no dialogue.

CREATE TABLE quotes (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    category     VARCHAR(12)  NOT NULL,
    media_type   VARCHAR(5)   NOT NULL,
    tmdb_id      BIGINT       NOT NULL,
    title        VARCHAR(200) NOT NULL,
    release_year INT          NOT NULL,
    speaker      VARCHAR(100) NOT NULL,
    line         VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_quotes_category (category)
);
