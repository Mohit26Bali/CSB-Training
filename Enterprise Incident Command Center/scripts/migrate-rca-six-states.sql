-- One-off migration of an EXISTING icc_rca database to the six-step problem (D31). Run once, in MySQL Workbench or the mysql client.
-- A fresh database does not need it: schema.sql already creates the new shape.
-- Safe to read first: it only touches icc_rca.problem. Take a backup if the data matters.

USE icc_rca;

ALTER TABLE problem
    MODIFY COLUMN status VARCHAR(30) NOT NULL,
    ADD COLUMN fix_notes          TEXT,
    ADD COLUMN resolution_code    VARCHAR(20),
    ADD COLUMN duplicate_of_id    BIGINT,
    ADD COLUMN workaround_applied BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN assigned_to_id     BIGINT,
    ADD COLUMN impact             INT NOT NULL DEFAULT 3,
    ADD COLUMN urgency            INT NOT NULL DEFAULT 3,
    ADD COLUMN priority           INT NOT NULL DEFAULT 5,
    ADD COLUMN reopen_count       INT NOT NULL DEFAULT 0,
    ADD COLUMN resolved_at        DATETIME(6);

-- Old status to new status (DRAFT = still being analysed, SUBMITTED = analysis finished, CLOSED = closed).
UPDATE problem SET status = 'ROOT_CAUSE_ANALYSIS' WHERE status = 'DRAFT';
UPDATE problem SET status = 'RESOLVED', resolution_code = 'FIX_APPLIED', resolved_at = updated_at,
                   fix_notes = COALESCE(fix_notes, workaround) WHERE status = 'SUBMITTED';
UPDATE problem SET resolution_code = 'FIX_APPLIED', resolved_at = COALESCE(closed_at, updated_at),
                   fix_notes = COALESCE(fix_notes, workaround) WHERE status = 'CLOSED';
-- A problem in ROOT_CAUSE_ANALYSIS or later has an assignee in ServiceNow. Use the creator for the old ones.
UPDATE problem SET assigned_to_id = created_by_id WHERE assigned_to_id IS NULL AND status <> 'NEW';

SELECT id, number, status, resolution_code, assigned_to_id, priority FROM problem ORDER BY id;
