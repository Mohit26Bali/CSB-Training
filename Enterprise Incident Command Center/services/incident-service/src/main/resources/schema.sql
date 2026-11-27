-- Tables of the Incident service (schema icc_incident).
-- Safe to run on every start: CREATE TABLE IF NOT EXISTS.
-- Columns marked (sync) exist from the start for the ServiceNow sync in Phase 4.

CREATE TABLE IF NOT EXISTS incident (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    number            VARCHAR(20)  NOT NULL,
    short_description VARCHAR(160) NOT NULL,
    description       TEXT         NOT NULL,
    category          VARCHAR(50)  NOT NULL,
    subcategory       VARCHAR(50),
    impact            INT          NOT NULL,
    urgency           INT          NOT NULL,
    priority          INT          NOT NULL,
    state             VARCHAR(20)  NOT NULL,
    reporter_id       BIGINT       NOT NULL,
    assigned_team_id  BIGINT       NOT NULL,
    assigned_to_id    BIGINT,
    hold_reason       VARCHAR(20),
    resolution_code   VARCHAR(60),
    resolution_notes  TEXT,
    major_incident_id BIGINT,
    created_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at        DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at       DATETIME(6),
    closed_at         DATETIME(6),
    external_id       VARCHAR(64),
    external_number   VARCHAR(40),
    sync_status       VARCHAR(20)  NOT NULL DEFAULT 'LOCAL_ONLY',
    last_synced_at    DATETIME(6),
    correlation_id    VARCHAR(64),
    last_sync_error   VARCHAR(500),
    reporter_email    VARCHAR(100),
    assigned_to_email VARCHAR(100),
    assigned_team_name VARCHAR(60),
    PRIMARY KEY (id),
    UNIQUE KEY uk_incident_number (number),
    KEY idx_incident_reporter (reporter_id),
    KEY idx_incident_team_state (assigned_team_id, state),
    KEY idx_incident_created (created_at)
);

CREATE TABLE IF NOT EXISTS incident_comment (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    incident_id BIGINT      NOT NULL,
    type        VARCHAR(20) NOT NULL,
    body        TEXT        NOT NULL,
    author_id   BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sync_status VARCHAR(20) NOT NULL DEFAULT 'LOCAL_ONLY',
    PRIMARY KEY (id),
    KEY idx_comment_incident (incident_id),
    CONSTRAINT fk_comment_incident FOREIGN KEY (incident_id) REFERENCES incident (id)
);

CREATE TABLE IF NOT EXISTS audit_event (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    actor_id       BIGINT,
    action         VARCHAR(60)  NOT NULL,
    entity_type    VARCHAR(40)  NOT NULL,
    entity_id      BIGINT,
    details        VARCHAR(500),
    correlation_id VARCHAR(64),
    created_at     DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_audit_created (created_at)
);
