-- Microservices Architecture Lab (Phase 9). Isolated, disposable tables: every row belongs to ONE lab run (run_key) and is removed
-- when that run ends. Nothing here references, or is referenced by, a SpringLab application table.
--
-- The tables are grouped by the (simulated) service that OWNS them. This is LOGICAL isolation inside one MySQL schema, not a
-- separate database server per service. Rows reference rows of other services by plain id with NO foreign key on purpose:
-- a real service cannot rely on a constraint that spans another service's database. Foreign keys exist only inside one service.

-- Run lifecycle (lab infrastructure, not a service): who owns a run, which state it is in and its stored result.
CREATE TABLE micro_lab_runs (
    run_key       VARCHAR(32)  NOT NULL,
    owner         VARCHAR(64)  NOT NULL,
    experiment_id VARCHAR(64)  NOT NULL,
    what_if       VARCHAR(500) NOT NULL,
    state         VARCHAR(16)  NOT NULL,
    result_json   MEDIUMTEXT   NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (run_key),
    KEY idx_micro_lab_runs_owner (owner, created_at),
    CONSTRAINT chk_micro_lab_runs_state CHECK (state IN ('CREATED', 'EXECUTING', 'COMPLETED', 'FAILED'))
) ENGINE = InnoDB;

-- User service
CREATE TABLE micro_lab_users (
    id           BIGINT        NOT NULL AUTO_INCREMENT,
    run_key      VARCHAR(32)   NOT NULL,
    name         VARCHAR(80)   NOT NULL,
    credit_limit DECIMAL(10,2) NOT NULL,
    created_at   DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_micro_lab_users_run (run_key)
) ENGINE = InnoDB;

-- Product service
CREATE TABLE micro_lab_products (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)   NOT NULL,
    sku        VARCHAR(40)   NOT NULL,
    name       VARCHAR(80)   NOT NULL,
    price      DECIMAL(10,2) NOT NULL,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_products_sku (run_key, sku)
) ENGINE = InnoDB;

-- Order service: orders, saga progress and the transactional outbox all live in the Order service's own database.
CREATE TABLE micro_lab_orders (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    run_key         VARCHAR(32)   NOT NULL,
    user_ref        BIGINT        NULL,
    product_ref     BIGINT        NULL,
    quantity        INT           NOT NULL,
    status          VARCHAR(24)   NOT NULL,
    idempotency_key VARCHAR(64)   NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_orders_idem (run_key, idempotency_key),
    KEY idx_micro_lab_orders_run (run_key)
) ENGINE = InnoDB;

CREATE TABLE micro_lab_saga_steps (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)  NOT NULL,
    order_id   BIGINT       NOT NULL,
    seq        INT          NOT NULL,
    step       VARCHAR(40)  NOT NULL,
    state      VARCHAR(24)  NOT NULL,
    detail     VARCHAR(160) NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_micro_lab_saga_run (run_key),
    CONSTRAINT fk_micro_lab_saga_order FOREIGN KEY (order_id) REFERENCES micro_lab_orders (id)
) ENGINE = InnoDB;

-- Transactional outbox: written in the SAME local transaction as the business change, published later by a separate step.
CREATE TABLE micro_lab_outbox (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    run_key     VARCHAR(32)  NOT NULL,
    message_id  VARCHAR(40)  NOT NULL,
    aggregate   VARCHAR(40)  NOT NULL,
    event_type  VARCHAR(40)  NOT NULL,
    payload     VARCHAR(400) NOT NULL,
    status      VARCHAR(16)  NOT NULL,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_outbox_msg (run_key, message_id)
) ENGINE = InnoDB;

-- Inventory service
CREATE TABLE micro_lab_stock (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    run_key     VARCHAR(32) NOT NULL,
    product_ref BIGINT      NOT NULL,
    available   INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_stock_product (run_key, product_ref),
    CONSTRAINT chk_micro_lab_stock_available CHECK (available >= 0)
) ENGINE = InnoDB;

CREATE TABLE micro_lab_reservations (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32) NOT NULL,
    order_ref  BIGINT      NOT NULL,
    stock_id   BIGINT      NOT NULL,
    quantity   INT         NOT NULL,
    status     VARCHAR(16) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_reservations_order (run_key, order_ref),
    CONSTRAINT fk_micro_lab_reservations_stock FOREIGN KEY (stock_id) REFERENCES micro_lab_stock (id)
) ENGINE = InnoDB;

-- Payment service
CREATE TABLE micro_lab_payments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    run_key         VARCHAR(32)   NOT NULL,
    order_ref       BIGINT        NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    status          VARCHAR(16)   NOT NULL,
    idempotency_key VARCHAR(64)   NULL,
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_payments_idem (run_key, idempotency_key),
    KEY idx_micro_lab_payments_run (run_key)
) ENGINE = InnoDB;

-- Notification service: its log plus the ids of the messages it has already processed (idempotent consumer).
CREATE TABLE micro_lab_notifications (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)  NOT NULL,
    order_ref  BIGINT       NOT NULL,
    kind       VARCHAR(40)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_micro_lab_notifications_run (run_key)
) ENGINE = InnoDB;

CREATE TABLE micro_lab_processed_events (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32) NOT NULL,
    consumer   VARCHAR(40) NOT NULL,
    message_id VARCHAR(40) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_processed (run_key, consumer, message_id)
) ENGINE = InnoDB;

-- Service registry (a simulated registry: it is this table plus code, not Eureka or Consul)
CREATE TABLE micro_lab_registry (
    id             BIGINT      NOT NULL AUTO_INCREMENT,
    run_key        VARCHAR(32) NOT NULL,
    service        VARCHAR(40) NOT NULL,
    instance       VARCHAR(40) NOT NULL,
    address        VARCHAR(80) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    last_heartbeat INT         NOT NULL,
    created_at     DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_micro_lab_registry_instance (run_key, service, instance)
) ENGINE = InnoDB;
