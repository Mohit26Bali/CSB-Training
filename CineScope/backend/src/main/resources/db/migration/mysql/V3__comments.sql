-- V3 (MySQL): reviews and discussion - comments, replies, ratings and "helpful" votes on titles.
--
-- Table <-> entity:  comments <-> entity/Comment, comment_votes <-> entity/CommentVote.
-- Never edit a script that has been applied; the matching copy for the automated tests is in db/migration/h2.
--
--   * A comment belongs to a title (media_type + tmdb_id, the same identity the catalogue and the watchlist use) and to its
--     author. A REPLY has parent_id set; replies are one level deep (a reply cannot be replied to).
--   * rating (1-10) is only on top-level comments, and a person has at most one rated comment per title (a rule of
--     service/CommentService, not of the table).
--   * sentiment / sentiment_score and processing are the result of service/CommentProcessor, kept so the page can show how the
--     comment was judged without doing the work again. processing holds one "stage|outcome|detail" line per stage.
--   * status: PUBLISHED (visible to everyone), PENDING (held for an administrator) or REJECTED (hidden; only the author sees it).
--   * helpful_count is a copy of the number of rows in comment_votes, kept so lists can be sorted by it cheaply.

CREATE TABLE comments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    user_id         BIGINT        NOT NULL,
    tmdb_id         BIGINT        NOT NULL,
    media_type      VARCHAR(5)    NOT NULL,
    parent_id       BIGINT        NULL,
    body            VARCHAR(1000) NOT NULL,
    rating          INT           NULL,
    sentiment       VARCHAR(10)   NOT NULL,
    sentiment_score DOUBLE        NOT NULL,
    status          VARCHAR(10)   NOT NULL,
    processing      VARCHAR(1000) NOT NULL,
    helpful_count   INT           NOT NULL,
    created_at      DATETIME(6)   NOT NULL,
    updated_at      DATETIME(6)   NOT NULL,
    PRIMARY KEY (id),
    KEY idx_comments_title (media_type, tmdb_id, status, created_at),
    KEY idx_comments_parent (parent_id),
    KEY idx_comments_user (user_id, created_at),
    CONSTRAINT fk_comments_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_comments_parent FOREIGN KEY (parent_id) REFERENCES comments (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE comment_votes (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    comment_id BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_comment_votes UNIQUE (comment_id, user_id),
    CONSTRAINT fk_votes_comment FOREIGN KEY (comment_id) REFERENCES comments (id),
    CONSTRAINT fk_votes_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
