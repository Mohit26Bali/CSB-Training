-- =====================================================================
-- V2: refresh tokens
--
-- The ACCESS token (JWT) is never stored: the server checks its signature.
-- The REFRESH token is a long random string that IS stored (as a SHA-256
-- hash), so the server can revoke it.
--
-- Rotation: every refresh replaces the token with a new one in the same
-- family (one family = one login on one device). Presenting an already
-- replaced token means it was copied, so the whole family is revoked.
-- =====================================================================

CREATE TABLE refresh_tokens (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    user_id        BIGINT      NOT NULL,
    token_hash     CHAR(64)    NOT NULL,
    family_id      CHAR(36)    NOT NULL,              -- UUID shared by all rotations of one login
    expires_at     DATETIME    NOT NULL,
    created_at     DATETIME    NOT NULL,
    revoked_at     DATETIME    NULL,                  -- NULL = still usable
    revoked_reason VARCHAR(20) NULL,                  -- ROTATED, LOGOUT, REUSE_DETECTED, ADMIN
    CONSTRAINT pk_refresh_tokens PRIMARY KEY (id),
    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);
