-- Demo data of the Incident service. Loaded only with the "demo" profile (D6).
-- Fixed ids: the Major Incident and RCA demo data refer to these ids. Do not change them.
-- Users: 1 admin, 2 sd.engineer, 3 net.engineer, 4 db.engineer, 5 app.engineer, 6 employee1, 7 employee2.
-- Teams: 1 Service Desk, 2 Network, 3 Database, 4 Applications.
-- priority = impact + urgency - 1. Dates are relative to now, so the dashboard trends are never empty.
-- "ON DUPLICATE KEY UPDATE id = id" means: insert if missing, otherwise leave the row alone.

INSERT INTO incident (id, number, short_description, description, category, impact, urgency, priority, state,
                      reporter_id, assigned_team_id, assigned_to_id, resolution_code, resolution_notes,
                      major_incident_id, created_at, updated_at, resolved_at, closed_at) VALUES
 (1,  'INC0000001', 'Core switch down on floor 3', 'All users on floor 3 have lost network access.', 'Network', 1, 1, 1, 'IN_PROGRESS',
      6, 2, 3, NULL, NULL, 1, NOW(6) - INTERVAL 2 HOUR, NOW(6) - INTERVAL 30 MINUTE, NULL, NULL),
 (2,  'INC0000002', 'Primary database not reachable', 'The orders application cannot connect to the primary database.', 'Database', 1, 1, 1, 'RESOLVED',
      7, 3, 4, 'Solution provided', 'Failover to the standby node and restart of the primary.', 2, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 2 DAY, NOW(6) - INTERVAL 2 DAY, NULL),
 (3,  'INC0000003', 'Wi-Fi drops in meeting room B', 'The Wi-Fi disconnects every few minutes.', 'Network', 2, 2, 3, 'NEW',
      6, 2, NULL, NULL, NULL, NULL, NOW(6) - INTERVAL 5 HOUR, NOW(6) - INTERVAL 5 HOUR, NULL, NULL),
 (4,  'INC0000004', 'Laptop battery not charging', 'The laptop shows plugged in, not charging.', 'Hardware', 3, 2, 4, 'NEW',
      7, 1, NULL, NULL, NULL, NULL, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 1 DAY, NULL, NULL),
 (5,  'INC0000005', 'Payroll report shows wrong totals', 'The monthly payroll report adds up to a wrong total.', 'Software', 1, 2, 2, 'IN_PROGRESS',
      6, 4, 5, NULL, NULL, NULL, NOW(6) - INTERVAL 1 DAY, NOW(6) - INTERVAL 20 HOUR, NULL, NULL),
 (6,  'INC0000006', 'Cannot log in to the HR portal', 'Login fails with "account locked".', 'Access', 2, 2, 3, 'IN_PROGRESS',
      7, 1, 2, NULL, NULL, NULL, NOW(6) - INTERVAL 2 DAY, NOW(6) - INTERVAL 1 DAY, NULL, NULL),
 (7,  'INC0000007', 'Slow queries in the reporting database', 'Reports take over a minute to load.', 'Database', 2, 1, 2, 'NEW',
      6, 3, NULL, NULL, NULL, NULL, NOW(6) - INTERVAL 3 HOUR, NOW(6) - INTERVAL 3 HOUR, NULL, NULL),
 (8,  'INC0000008', 'Mail client crashes on start', 'The mail client closes right after it opens.', 'Software', 3, 2, 4, 'RESOLVED',
      7, 4, 5, 'Solution provided', 'Profile rebuilt after a corrupt cache file was removed.', NULL, NOW(6) - INTERVAL 4 DAY, NOW(6) - INTERVAL 1 HOUR, NOW(6) - INTERVAL 1 HOUR, NULL),
 (9,  'INC0000009', 'Replace broken keyboard', 'Several keys do not respond.', 'Hardware', 3, 3, 5, 'CLOSED',
      6, 1, 2, 'Solution provided', 'Keyboard replaced from stock.', NULL, NOW(6) - INTERVAL 6 DAY, NOW(6) - INTERVAL 4 DAY, NOW(6) - INTERVAL 5 DAY, NOW(6) - INTERVAL 4 DAY),
 (10, 'INC0000010', 'Request access to the shared finance folder', 'Needs read access for the audit period.', 'Access', 3, 2, 4, 'CLOSED',
      7, 1, 2, 'Solution provided', 'Access granted after manager approval.', NULL, NOW(6) - INTERVAL 5 DAY, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 4 DAY, NOW(6) - INTERVAL 3 DAY),
 (11, 'INC0000011', 'VPN disconnects every hour', 'The VPN session drops after exactly one hour.', 'Network', 2, 1, 2, 'CLOSED',
      6, 2, 3, 'Workaround provided', 'Session timeout raised on the VPN gateway.', NULL, NOW(6) - INTERVAL 6 DAY, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 4 DAY, NOW(6) - INTERVAL 3 DAY),
 (12, 'INC0000012', 'Request a second monitor', 'Would like a second monitor for the desk.', 'Other', 3, 3, 5, 'NEW',
      7, 1, NULL, NULL, NULL, NULL, NOW(6) - INTERVAL 7 HOUR, NOW(6) - INTERVAL 7 HOUR, NULL, NULL)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO incident_comment (id, incident_id, type, body, author_id, created_at) VALUES
 (1, 1, 'COMMENT',   'We are working on it. Please use the guest network on floor 2 for now.', 3, NOW(6) - INTERVAL 90 MINUTE),
 (2, 1, 'WORK_NOTE', 'Switch is not responding on the console. Checking the power supply and uplinks.', 3, NOW(6) - INTERVAL 80 MINUTE),
 (3, 2, 'WORK_NOTE', 'Primary node out of memory. Standby promoted.', 4, NOW(6) - INTERVAL 3 DAY + INTERVAL 20 MINUTE),
 (4, 2, 'COMMENT',   'The database is available again. Please confirm that your application works.', 4, NOW(6) - INTERVAL 2 DAY),
 (5, 5, 'WORK_NOTE', 'The total ignores rows with a null bonus. Looking at the report query.', 5, NOW(6) - INTERVAL 20 HOUR),
 (6, 6, 'COMMENT',   'We have reset the lock. Please try again and tell us if it still fails.', 2, NOW(6) - INTERVAL 1 DAY),
 (7, 8, 'COMMENT',   'Fixed. Please tell us if it comes back.', 5, NOW(6) - INTERVAL 1 DAY),
 (8, 11, 'WORK_NOTE', 'Idle session timeout on the gateway was 60 minutes. Raised to 8 hours.', 3, NOW(6) - INTERVAL 4 DAY)
ON DUPLICATE KEY UPDATE id = id;
