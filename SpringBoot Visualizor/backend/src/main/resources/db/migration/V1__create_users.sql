CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(50)  NOT NULL,
    email         VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email),
    CONSTRAINT ck_users_role     CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
