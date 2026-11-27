-- Challenge Arena: topics, questions, options, attempts and the questions/answers belonging to each attempt.
-- VARCHAR (not TEXT) is used for long strings so Hibernate's ddl-auto=validate accepts the mapping to java.lang.String.

CREATE TABLE topics (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(100) NOT NULL,
    slug        VARCHAR(60)  NOT NULL,
    description VARCHAR(500) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_topics_name UNIQUE (name),
    CONSTRAINT uk_topics_slug UNIQUE (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- The slugs are a stable contract: learning modules link to /arena?topic=<slug> as their "Test yourself" target.
INSERT INTO topics (name, slug, description) VALUES
    ('Java Core', 'java-core', 'Language fundamentals and the standard library.'),
    ('Spring Boot Basics', 'spring-boot-basics', 'Beans, dependency injection, auto-configuration and externalized configuration.'),
    ('REST Request Lifecycle', 'rest-lifecycle', 'How Spring MVC handles an HTTP request from DispatcherServlet to the response body.'),
    ('JWT and Spring Security', 'jwt-security', 'Authentication, JSON Web Tokens and authorization decisions.'),
    ('JPA and Hibernate', 'jpa-hibernate', 'Entity mapping, relationships, fetching and the persistence context.');

CREATE TABLE questions (
    id            BIGINT        NOT NULL AUTO_INCREMENT,
    topic_id      BIGINT        NOT NULL,
    difficulty    VARCHAR(10)   NOT NULL,
    question_text VARCHAR(2000) NOT NULL,
    explanation   VARCHAR(4000) NOT NULL,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_by    BIGINT        NULL, -- NULL = authored by SpringLab (seed content); only an ADMIN may edit those
    created_at    DATETIME(6)   NOT NULL,
    updated_at    DATETIME(6)   NOT NULL,
    version       BIGINT        NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT fk_questions_topic   FOREIGN KEY (topic_id)   REFERENCES topics (id),
    CONSTRAINT fk_questions_creator FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_questions_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    INDEX ix_questions_selection (topic_id, difficulty, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE question_options (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    question_id BIGINT       NOT NULL,
    option_text VARCHAR(500) NOT NULL,
    correct     BOOLEAN      NOT NULL,
    sort_order  INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_options_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE CASCADE,
    INDEX ix_options_question (question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE attempts (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    topic_id        BIGINT      NULL,      -- NULL = questions drawn from every topic
    difficulty      VARCHAR(10) NULL,      -- NULL = any difficulty
    status          VARCHAR(15) NOT NULL,
    started_at      DATETIME(6) NOT NULL,
    submitted_at    DATETIME(6) NULL,
    total_questions INT         NOT NULL,
    score           INT         NULL,      -- number of correct answers; NULL until submitted
    PRIMARY KEY (id),
    CONSTRAINT fk_attempts_user  FOREIGN KEY (user_id)  REFERENCES users (id),
    CONSTRAINT fk_attempts_topic FOREIGN KEY (topic_id) REFERENCES topics (id),
    CONSTRAINT ck_attempts_status     CHECK (status IN ('IN_PROGRESS', 'SUBMITTED')),
    CONSTRAINT ck_attempts_difficulty CHECK (difficulty IS NULL OR difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    INDEX ix_attempts_user_status (user_id, status),
    INDEX ix_attempts_topic (topic_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per question of an attempt, created when the attempt starts (this fixes which questions belong to it).
CREATE TABLE attempt_answers (
    id                 BIGINT  NOT NULL AUTO_INCREMENT,
    attempt_id         BIGINT  NOT NULL,
    question_id        BIGINT  NOT NULL,
    sort_order         INT     NOT NULL,
    selected_option_id BIGINT  NULL,      -- NULL = skipped (or the option was later removed by a teacher)
    correct            BOOLEAN NULL,      -- snapshot taken at submission; never recomputed
    PRIMARY KEY (id),
    CONSTRAINT fk_answers_attempt  FOREIGN KEY (attempt_id)         REFERENCES attempts (id) ON DELETE CASCADE,
    CONSTRAINT fk_answers_question FOREIGN KEY (question_id)        REFERENCES questions (id),
    CONSTRAINT fk_answers_option   FOREIGN KEY (selected_option_id) REFERENCES question_options (id) ON DELETE SET NULL,
    CONSTRAINT uk_answers_attempt_question UNIQUE (attempt_id, question_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
