-- Tables of the Assignment service (schema icc_assignment).
-- Safe to run on every start: CREATE TABLE IF NOT EXISTS.
-- audit_event uses the shared AuditEvent entity from common-lib.

CREATE TABLE IF NOT EXISTS team (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    external_id VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_team_name (name)
);

CREATE TABLE IF NOT EXISTS app_user (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    role          VARCHAR(30)  NOT NULL,
    team_id       BIGINT,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    external_id   VARCHAR(64),
    PRIMARY KEY (id),
    UNIQUE KEY uk_app_user_username (username),
    CONSTRAINT fk_app_user_team FOREIGN KEY (team_id) REFERENCES team (id)
);

CREATE TABLE IF NOT EXISTS assignment_rule (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    category VARCHAR(50) NOT NULL,
    team_id  BIGINT      NOT NULL,
    active   BOOLEAN     NOT NULL DEFAULT TRUE,
    PRIMARY KEY (id),
    UNIQUE KEY uk_assignment_rule_category (category),
    CONSTRAINT fk_assignment_rule_team FOREIGN KEY (team_id) REFERENCES team (id)
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
