-- JPA Relationship Lab: isolated tables used ONLY by the learning experiments (prefix jpa_lab_).
-- They hold no application data. Every experiment run inserts rows tagged with its own run_key and removes them again;
-- nothing here is referenced by users, challenges or any other part of SpringLab.
--
-- The foreign keys are real database constraints on purpose: several experiments show MySQL itself enforcing them.

-- One-to-many / many-to-one: Department 1 --- * Employee (the foreign key lives in the "many" table).
CREATE TABLE jpa_lab_department (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(32) NOT NULL,
    name    VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_department_run (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jpa_lab_employee (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    run_key       VARCHAR(32) NOT NULL,
    name          VARCHAR(80) NOT NULL,
    department_id BIGINT      NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_employee_run (run_key),
    CONSTRAINT fk_jpa_lab_employee_department FOREIGN KEY (department_id) REFERENCES jpa_lab_department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- A second child collection of Department, used to show what fetching two collections in one query does.
CREATE TABLE jpa_lab_room (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    run_key       VARCHAR(32) NOT NULL,
    label         VARCHAR(80) NOT NULL,
    department_id BIGINT      NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_room_run (run_key),
    CONSTRAINT fk_jpa_lab_room_department FOREIGN KEY (department_id) REFERENCES jpa_lab_department (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One-to-one: Account 1 --- 1 Profile (the foreign key is on the profile and is UNIQUE).
CREATE TABLE jpa_lab_account (
    id       BIGINT      NOT NULL AUTO_INCREMENT,
    run_key  VARCHAR(32) NOT NULL,
    username VARCHAR(60) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_account_run (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jpa_lab_profile (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32)  NOT NULL,
    bio        VARCHAR(200) NOT NULL,
    account_id BIGINT       NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_jpa_lab_profile_account UNIQUE (account_id),
    KEY ix_jpa_lab_profile_run (run_key),
    CONSTRAINT fk_jpa_lab_profile_account FOREIGN KEY (account_id) REFERENCES jpa_lab_account (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Many-to-many: Student * --- * Course through the join table jpa_lab_enrollment.
CREATE TABLE jpa_lab_student (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(32) NOT NULL,
    name    VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_student_run (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jpa_lab_course (
    id      BIGINT       NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(32)  NOT NULL,
    title   VARCHAR(80)  NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_course_run (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jpa_lab_enrollment (
    student_id BIGINT NOT NULL,
    course_id  BIGINT NOT NULL,
    PRIMARY KEY (student_id, course_id),
    CONSTRAINT fk_jpa_lab_enrollment_student FOREIGN KEY (student_id) REFERENCES jpa_lab_student (id),
    CONSTRAINT fk_jpa_lab_enrollment_course FOREIGN KEY (course_id) REFERENCES jpa_lab_course (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Cascade ALL without orphanRemoval: Project 1 --- * Task.
CREATE TABLE jpa_lab_project (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(32) NOT NULL,
    name    VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_project_run (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jpa_lab_task (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    run_key    VARCHAR(32) NOT NULL,
    title      VARCHAR(80) NOT NULL,
    project_id BIGINT      NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_task_run (run_key),
    CONSTRAINT fk_jpa_lab_task_project FOREIGN KEY (project_id) REFERENCES jpa_lab_project (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Cascade ALL with orphanRemoval: Team 1 --- * Member.
CREATE TABLE jpa_lab_team (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(32) NOT NULL,
    name    VARCHAR(80) NOT NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_team_run (run_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE jpa_lab_member (
    id      BIGINT      NOT NULL AUTO_INCREMENT,
    run_key VARCHAR(32) NOT NULL,
    name    VARCHAR(80) NOT NULL,
    team_id BIGINT      NULL,
    PRIMARY KEY (id),
    KEY ix_jpa_lab_member_run (run_key),
    CONSTRAINT fk_jpa_lab_member_team FOREIGN KEY (team_id) REFERENCES jpa_lab_team (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
