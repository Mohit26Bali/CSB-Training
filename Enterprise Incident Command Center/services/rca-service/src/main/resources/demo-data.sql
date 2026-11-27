-- Demo data of the RCA service. Loaded only with the "demo" profile (D6).
-- major_incident_id and incident_id refer to the Major Incident and Incident demo data. Do not change the ids.
-- Four problems, one in each early step, so every button can be shown (D31):
--   1 is in ROOT_CAUSE_ANALYSIS on the resolved database outage (major incident 2): press Fix, Resolve, Complete.
--   2 is RESOLVED (fix applied), with one action done and one open: press Complete, or Re-analyze.
--   3 is NEW and unassigned: press Assess.
--   4 is in ASSESS: press Confirm, Mark duplicate or Cancel.

INSERT INTO problem (id, number, title, description, status, root_cause, root_cause_category, workaround, fix_notes,
                     resolution_code, assigned_to_id, impact, urgency, priority, major_incident_id, created_by_id,
                     resolved_at, created_at, updated_at) VALUES
 (1, 'PRB0000001', 'Primary database ran out of memory', 'The primary database stopped answering and the orders application was down. Find out why.',
     'ROOT_CAUSE_ANALYSIS', NULL, NULL, NULL, NULL, NULL, 1, 1, 1, 1, 2, 1,
     NULL, NOW(6) - INTERVAL 2 DAY, NOW(6) - INTERVAL 2 DAY),
 (2, 'PRB0000002', 'VPN sessions drop after one hour', 'Several users lost their VPN session every hour.',
     'RESOLVED', 'The idle session timeout on the VPN gateway was set to 60 minutes after a firmware update reset the setting.',
     'Configuration', 'Reconnect the VPN.', 'The timeout was raised to 8 hours.', 'FIX_APPLIED', 1, 2, 2, 3, NULL, 1,
     NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 1 DAY),
 (3, 'PRB0000003', 'Printers on floor 3 go offline every Monday', 'The floor 3 printers drop off the network after the weekend.',
     'NEW', NULL, NULL, NULL, NULL, NULL, NULL, 3, 3, 5, NULL, 1,
     NULL, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY),
 (4, 'PRB0000004', 'Email sync fails for the sales team', 'Mail on phones stops syncing for several people in sales.',
     'ASSESS', NULL, NULL, NULL, NULL, NULL, 1, 2, 2, 3, NULL, 1,
     NULL, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO corrective_action (id, problem_id, description, owner_id, due_date, status) VALUES
 (1, 2, 'Add the VPN session timeout to the firmware upgrade checklist.', 3, CURRENT_DATE + INTERVAL 7 DAY, 'DONE'),
 (2, 2, 'Add an alert when the VPN session timeout differs from the standard value.', 3, CURRENT_DATE + INTERVAL 14 DAY, 'OPEN')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO problem_incident (problem_id, incident_id) VALUES
 (1, 2),
 (2, 11),
 (3, 11),
 (4, 11)
ON DUPLICATE KEY UPDATE problem_id = problem_id;
