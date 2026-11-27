-- Puts the demo data back to its starting state (D6). Run it with all five services STOPPED, then start them again
-- with the demo profile (they reload the demo data). The users, teams and assignment rules are not touched.
--
--   PowerShell:  Get-Content scripts/reset-demo-data.sql | mysql -u icc -p
--   Git Bash:    mysql -u icc -p < scripts/reset-demo-data.sql
--
-- It only deletes rows. The tables stay, so it is safe to run at any time.

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM icc_incident.incident_comment;
DELETE FROM icc_incident.incident;
DELETE FROM icc_incident.audit_event;

DELETE FROM icc_major_incident.timeline_entry;
DELETE FROM icc_major_incident.major_incident;
DELETE FROM icc_major_incident.audit_event;

DELETE FROM icc_rca.problem_incident;
DELETE FROM icc_rca.corrective_action;
DELETE FROM icc_rca.problem;
DELETE FROM icc_rca.audit_event;

DELETE FROM icc_assignment.audit_event;

-- Counting starts again at 1, so the next new incident is INC0000013 after the 12 demo incidents.
ALTER TABLE icc_incident.incident AUTO_INCREMENT = 1;
ALTER TABLE icc_incident.incident_comment AUTO_INCREMENT = 1;
ALTER TABLE icc_major_incident.major_incident AUTO_INCREMENT = 1;
ALTER TABLE icc_major_incident.timeline_entry AUTO_INCREMENT = 1;
ALTER TABLE icc_rca.problem AUTO_INCREMENT = 1;
ALTER TABLE icc_rca.corrective_action AUTO_INCREMENT = 1;

SET FOREIGN_KEY_CHECKS = 1;
