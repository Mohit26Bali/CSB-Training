-- V5 (MySQL): which quotes each signed-in person has already been shown, so the quiz does not repeat itself.
--
-- Table <-> entity:  quote_seen <-> entity/QuoteSeen, used by service/QuoteService. Never edit a script that has been applied; the
-- matching copy for the automated tests is in db/migration/h2.
--
--   * One row per (person, quote). The pair is unique, so a quote is recorded at most once per round. When a person has seen every
--     quote of a category, service/QuoteService clears that category's rows and a new round starts.
--   * Both foreign keys cascade: deleting an account deletes its history (no change needed in the account-deletion code), and
--     removing a quote from the quotes table removes it from everyone's history.

CREATE TABLE quote_seen (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    user_id  BIGINT      NOT NULL,
    quote_id BIGINT      NOT NULL,
    seen_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_quote_seen UNIQUE (user_id, quote_id),
    CONSTRAINT fk_seen_user  FOREIGN KEY (user_id)  REFERENCES users (id)  ON DELETE CASCADE,
    CONSTRAINT fk_seen_quote FOREIGN KEY (quote_id) REFERENCES quotes (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
