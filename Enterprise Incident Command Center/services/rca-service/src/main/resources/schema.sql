-- Tables of the RCA service (schema icc_rca).
-- Safe to run on every start: CREATE TABLE IF NOT EXISTS.
-- major_incident_id and incident_id refer to records of other services by id only: no foreign key across schemas.

CREATE TABLE IF NOT EXISTS problem (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    number              VARCHAR(20)  NOT NULL,
    title               VARCHAR(200) NOT NULL,
    description         TEXT         NOT NULL,
    status              VARCHAR(30)  NOT NULL,
    root_cause          TEXT,
    root_cause_category VARCHAR(60),
    workaround          TEXT,
    fix_notes           TEXT,
    resolution_code     VARCHAR(20),
    duplicate_of_id     BIGINT,
    workaround_applied  BOOLEAN      NOT NULL DEFAULT FALSE,
    assigned_to_id      BIGINT,
    impact              INT          NOT NULL DEFAULT 3,
    urgency             INT          NOT NULL DEFAULT 3,
    priority            INT          NOT NULL DEFAULT 5,
    major_incident_id   BIGINT,
    reopen_count        INT          NOT NULL DEFAULT 0,
    created_by_id       BIGINT       NOT NULL,
    created_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    resolved_at         DATETIME(6),
    closed_at           DATETIME(6),
    external_id         VARCHAR(64),
    external_number     VARCHAR(40),
    sync_status         VARCHAR(20)  NOT NULL DEFAULT 'LOCAL_ONLY',
    last_synced_at      DATETIME(6),
    correlation_id      VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_problem_number (number),
    KEY idx_problem_status (status)
);

CREATE TABLE IF NOT EXISTS corrective_action (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    problem_id  BIGINT      NOT NULL,
    description TEXT        NOT NULL,
    owner_id    BIGINT      NOT NULL,
    due_date    DATE        NOT NULL,
    status      VARCHAR(10) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_action_problem (problem_id),
    CONSTRAINT fk_action_problem FOREIGN KEY (problem_id) REFERENCES problem (id)
);

-- Link table: one problem can cover many incidents. Both columns together are the key.
CREATE TABLE IF NOT EXISTS problem_incident (
    problem_id  BIGINT NOT NULL,
    incident_id BIGINT NOT NULL,
    PRIMARY KEY (problem_id, incident_id),
    CONSTRAINT fk_problem_incident_problem FOREIGN KEY (problem_id) REFERENCES problem (id)
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
