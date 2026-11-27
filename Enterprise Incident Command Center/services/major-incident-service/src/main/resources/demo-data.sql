-- Demo data of the Major Incident service. Loaded only with the "demo" profile (D6).
-- incident_id refers to the Incident demo data: 1 (core switch, still active) and 2 (primary database, resolved).
-- Do not change the ids.

INSERT INTO major_incident (id, incident_id, summary, impact_summary, commander_id, status, declared_by_id,
                            declared_at, resolved_at, closed_at) VALUES
 (1, 1, 'Core switch failure on floor 3', 'About 120 users on floor 3 have no network access. Sales and support are affected.',
     3, 'MITIGATING', 3, NOW(6) - INTERVAL 100 MINUTE, NULL, NULL),
 (2, 2, 'Primary database outage', 'The orders application was down for about 40 minutes. New orders could not be placed.',
     4, 'RESOLVED', 4, NOW(6) - INTERVAL 3 DAY, NOW(6) - INTERVAL 2 DAY, NULL)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO timeline_entry (id, major_incident_id, entry_type, text, author_id, created_at) VALUES
 (1, 1, 'STATUS_CHANGE', 'Major incident declared for INC0000001', 3, NOW(6) - INTERVAL 100 MINUTE),
 (2, 1, 'UPDATE',        'Network team on site. The switch does not respond on the console.', 3, NOW(6) - INTERVAL 90 MINUTE),
 (3, 1, 'STATUS_CHANGE', 'Status changed from DECLARED to MITIGATING', 3, NOW(6) - INTERVAL 60 MINUTE),
 (4, 1, 'UPDATE',        'Traffic moved to the standby switch. About half of the users are back online.', 3, NOW(6) - INTERVAL 30 MINUTE),
 (5, 2, 'STATUS_CHANGE', 'Major incident declared for INC0000002', 4, NOW(6) - INTERVAL 3 DAY),
 (6, 2, 'UPDATE',        'Primary database out of memory. Failing over to the standby node.', 4, NOW(6) - INTERVAL 3 DAY + INTERVAL 10 MINUTE),
 (7, 2, 'STATUS_CHANGE', 'Status changed from DECLARED to MITIGATING', 4, NOW(6) - INTERVAL 3 DAY + INTERVAL 15 MINUTE),
 (8, 2, 'UPDATE',        'Standby promoted. Orders are accepted again.', 4, NOW(6) - INTERVAL 3 DAY + INTERVAL 40 MINUTE),
 (9, 2, 'STATUS_CHANGE', 'Status changed from MITIGATING to RESOLVED', 4, NOW(6) - INTERVAL 2 DAY)
ON DUPLICATE KEY UPDATE id = id;
