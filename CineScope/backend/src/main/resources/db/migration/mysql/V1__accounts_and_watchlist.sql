-- V1 (MySQL): accounts and the personal watchlist.
--
-- Table <-> entity:  users <-> entity/User, watchlist_items <-> entity/WatchlistItem.
-- Hibernate only VALIDATES these tables (ddl-auto=validate); every future schema change needs a new V<n>__*.sql here AND the
-- matching copy in db/migration/h2 (used by the automated tests). Never edit a script that has been applied.
--
-- Design notes:
--   * watchlist_items stores a small SNAPSHOT of the title (name, poster, year) taken from TMDB when it is added, so the
--     watchlist page renders instantly without one TMDB request per item.
--   * The unique key (user_id, tmdb_id, media_type) makes "the same title twice" impossible, even under a double click.
--   * A movie and a TV show can share a TMDB id, which is why media_type is part of the key.

CREATE TABLE users (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    username     VARCHAR(50)  NOT NULL,
    email        VARCHAR(100) NOT NULL,
    password     VARCHAR(100) NOT NULL,
    display_name VARCHAR(60)  NOT NULL,
    region       VARCHAR(2)   NOT NULL,
    enabled      BIT(1)       NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE watchlist_items (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    tmdb_id      BIGINT       NOT NULL,
    media_type   VARCHAR(5)   NOT NULL,
    title        VARCHAR(255) NOT NULL,
    poster_path  VARCHAR(255),
    release_year INT,
    status       ENUM ('WANT', 'WATCHING', 'WATCHED') NOT NULL,
    rating       INT,
    note         VARCHAR(500),
    added_at     DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_watchlist_user_title UNIQUE (user_id, tmdb_id, media_type),
    KEY idx_watchlist_user_status (user_id, status, added_at),
    CONSTRAINT fk_watchlist_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
