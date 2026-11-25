-- V1: the initial schema (MySQL). Equals what Hibernate generated from the entity classes before Flyway was introduced.
--
-- How this file relates to the rest of the project:
--   * Table <-> entity:  users <-> entity/User, customers <-> entity/Customer, accounts <-> entity/Account,
--                        transactions <-> entity/Transaction, idempotency_keys <-> entity/IdempotencyRecord.
--   * Flyway runs it on an empty database. A database that already has these tables (created by Hibernate earlier) is
--     "baselined" at version 1 instead (spring.flyway.baseline-on-migrate), so this script is skipped there.
--   * Hibernate no longer changes the schema (ddl-auto=validate): on startup it only CHECKS that the entities match the
--     tables, and refuses to start if they do not. Every future schema change therefore needs a new V<n>__*.sql file
--     here AND the matching copy in db/migration/h2 (used by the tests). Never edit a migration that has been applied.

CREATE TABLE users (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    enabled   BIT(1)       NOT NULL,
    full_name VARCHAR(100) NOT NULL,
    password  VARCHAR(100) NOT NULL,
    role      ENUM ('ADMIN', 'CUSTOMER') NOT NULL,
    username  VARCHAR(50)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE customers (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    address    VARCHAR(255) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    email      VARCHAR(100) NOT NULL,
    mobile     VARCHAR(15)  NOT NULL,
    user_id    BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_customers_email UNIQUE (email),
    CONSTRAINT uk_customers_user UNIQUE (user_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE accounts (
    id             BIGINT        NOT NULL AUTO_INCREMENT,
    account_number VARCHAR(20)   NOT NULL,
    account_type   ENUM ('CURRENT', 'SAVINGS') NOT NULL,
    balance        DECIMAL(19, 2) NOT NULL,
    created_at     DATETIME(6)   NOT NULL,
    status         ENUM ('ACTIVE', 'BLOCKED', 'INACTIVE') NOT NULL,
    version        BIGINT        NOT NULL,
    customer_id    BIGINT        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_accounts_number UNIQUE (account_number),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transactions (
    id                          BIGINT         NOT NULL AUTO_INCREMENT,
    amount                      DECIMAL(19, 2) NOT NULL,
    balance_after               DECIMAL(19, 2) NOT NULL,
    counterparty_account_number VARCHAR(20),
    created_at                  DATETIME(6)    NOT NULL,
    description                 VARCHAR(255),
    direction                   ENUM ('CREDIT', 'DEBIT') NOT NULL,
    status                      ENUM ('FAILED', 'SUCCESS') NOT NULL,
    transaction_ref             VARCHAR(36)    NOT NULL,
    type                        ENUM ('DEPOSIT', 'TRANSFER', 'WITHDRAWAL') NOT NULL,
    account_id                  BIGINT         NOT NULL,
    PRIMARY KEY (id),
    KEY idx_txn_account_created (account_id, created_at),
    CONSTRAINT fk_transactions_account FOREIGN KEY (account_id) REFERENCES accounts (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE idempotency_keys (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    created_at      DATETIME(6)   NOT NULL,
    idempotency_key VARCHAR(64)   NOT NULL,
    request_hash    VARCHAR(64)   NOT NULL,
    response_body   VARCHAR(4000),
    username        VARCHAR(50)   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_idempotency_key_user UNIQUE (idempotency_key, username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
