-- Tables of the Major Incident service (schema icc_major_incident).
-- Safe to run on every start: CREATE TABLE IF NOT EXISTS.
-- incident_id refers to a record of the Incident service by id only: no foreign key across schemas.

CREATE TABLE IF NOT EXISTS major_incident (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    incident_id    BIGINT       NOT NULL,
    summary        VARCHAR(255) NOT NULL,
    impact_summary TEXT         NOT NULL,
    commander_id   BIGINT       NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    declared_by_id BIGINT       NOT NULL,
    declared_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at    DATETIME(6),
    closed_at      DATETIME(6),
    sync_status    VARCHAR(20)  NOT NULL DEFAULT 'LOCAL_ONLY',
    last_synced_at DATETIME(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_major_incident_incident (incident_id),
    KEY idx_major_incident_status (status)
);

CREATE TABLE IF NOT EXISTS timeline_entry (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    major_incident_id BIGINT      NOT NULL,
    entry_type        VARCHAR(20) NOT NULL,
    text              TEXT        NOT NULL,
    author_id         BIGINT      NOT NULL,
    created_at        DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sync_status       VARCHAR(20) NOT NULL DEFAULT 'LOCAL_ONLY',
    PRIMARY KEY (id),
    KEY idx_timeline_major_incident (major_incident_id),
    CONSTRAINT fk_timeline_major_incident FOREIGN KEY (major_incident_id) REFERENCES major_incident (id)
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
