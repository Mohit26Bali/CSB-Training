-- One-off migration of an EXISTING icc_incident database for the ServiceNow sync (D37, D38). Run once.
-- A fresh database does not need it: schema.sql already creates the new columns.
-- It adds four columns to incident and fills the three copies for the incidents that already exist, so they can be synced.
-- (This script may read the other schema, because it is a one-off migration. The running services never do.)

ALTER TABLE icc_incident.incident
    ADD COLUMN last_sync_error    VARCHAR(500),
    ADD COLUMN reporter_email     VARCHAR(100),
    ADD COLUMN assigned_to_email  VARCHAR(100),
    ADD COLUMN assigned_team_name VARCHAR(60);

UPDATE icc_incident.incident i JOIN icc_assignment.app_user u ON u.id = i.reporter_id
   SET i.reporter_email = u.username WHERE i.reporter_email IS NULL;
UPDATE icc_incident.incident i JOIN icc_assignment.app_user u ON u.id = i.assigned_to_id
   SET i.assigned_to_email = u.username WHERE i.assigned_to_id IS NOT NULL AND i.assigned_to_email IS NULL;
UPDATE icc_incident.incident i JOIN icc_assignment.team t ON t.id = i.assigned_team_id
   SET i.assigned_team_name = t.name WHERE i.assigned_team_name IS NULL;

SELECT id, number, state, reporter_email, assigned_to_email, assigned_team_name, sync_status FROM icc_incident.incident ORDER BY id LIMIT 6;
