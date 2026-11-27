-- JDBC Lab: isolated tables used ONLY by the learning experiments (prefix jdbc_lab_). They hold no application data.
-- Every experiment run inserts rows tagged with its own run_key and removes exactly those rows again. created_at lets the
-- startup cleanup remove rows left by a crashed run without touching rows of a run that is still active.
-- Real foreign keys and unique keys are used on purpose: several experiments show MySQL enforcing them.

CREATE TABLE jdbc_lab_customers (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)  NOT NULL,
    name       VARCHAR(80)  NOT NULL,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uk_jdbc_lab_customers_run_name UNIQUE (run_key, name),
    KEY ix_jdbc_lab_customers_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jdbc_lab_products (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)   NOT NULL,
    name       VARCHAR(80)   NOT NULL,
    price      DECIMAL(10,2) NOT NULL,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_jdbc_lab_products_run (run_key),
    KEY ix_jdbc_lab_products_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jdbc_lab_accounts (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)   NOT NULL,
    owner      VARCHAR(80)   NOT NULL,
    balance    DECIMAL(12,2) NOT NULL,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_jdbc_lab_accounts_run (run_key),
    KEY ix_jdbc_lab_accounts_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jdbc_lab_orders (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    run_key     VARCHAR(32) NOT NULL,
    customer_id BIGINT      NOT NULL,
    product_id  BIGINT      NOT NULL,
    quantity    INT         NOT NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_jdbc_lab_orders_run (run_key),
    KEY ix_jdbc_lab_orders_created (created_at),
    CONSTRAINT fk_jdbc_lab_orders_customer FOREIGN KEY (customer_id) REFERENCES jdbc_lab_customers (id),
    CONSTRAINT fk_jdbc_lab_orders_product FOREIGN KEY (product_id) REFERENCES jdbc_lab_products (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- A small ledger written together with account changes, so a transaction has more than one statement to commit or undo.
CREATE TABLE jdbc_lab_transactions (
    id         BIGINT        NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)   NOT NULL,
    account_id BIGINT        NOT NULL,
    delta      DECIMAL(12,2) NOT NULL,
    note       VARCHAR(80)   NOT NULL,
    created_at DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_jdbc_lab_transactions_run (run_key),
    KEY ix_jdbc_lab_transactions_created (created_at),
    CONSTRAINT fk_jdbc_lab_transactions_account FOREIGN KEY (account_id) REFERENCES jdbc_lab_accounts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jdbc_lab_batch_records (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32) NOT NULL,
    label      VARCHAR(80) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY ix_jdbc_lab_batch_run (run_key),
    KEY ix_jdbc_lab_batch_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
