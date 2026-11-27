-- Reference data of the Assignment service. Always loaded (D6).
-- Fixed ids: other services refer to these ids, so do not change them.
-- ON DUPLICATE KEY UPDATE id = id means "insert if missing, otherwise leave the row alone".
--
-- Demo login: every user below has the same local-only demo password (see README).
-- Only its BCrypt hash is stored here.

INSERT INTO team (id, name, description) VALUES
    (1, 'Service Desk',  'First line support'),
    (2, 'Network',       'Network and connectivity'),
    (3, 'Database',      'Databases and data services'),
    (4, 'Applications',  'Business applications')
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO app_user (id, username, password_hash, full_name, email, role, team_id) VALUES
    (1, 'mohit@icc.com',        '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Mohit Bali',        'mohit@icc.com',        'ADMIN',            NULL),
    (2, 'priya@icc.com',  '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Priya Nair',       'priya@icc.com',  'SUPPORT_ENGINEER', 1),
    (3, 'sai@icc.com', '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Sai Vivek',      'sai@icc.com', 'SUPPORT_ENGINEER', 2),
    (4, 'sachin@icc.com',  '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Sachin Suryavanshi',      'sachin@icc.com',  'SUPPORT_ENGINEER', 3),
    (5, 'vinod@icc.com', '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Vinod',  'vinod@icc.com', 'SUPPORT_ENGINEER', 4),
    (6, 'ananya@icc.com',    '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Ananya Iyer',      'ananya@icc.com',    'EMPLOYEE',         NULL),
    (7, 'rohan@icc.com',    '$2a$10$KFEGL0DNBVkY6EynIPt6futMYzNqhc4RGfeKkLpf3/EHPU9sMfoN2', 'Rohan Mehta',      'rohan@icc.com',    'EMPLOYEE',         NULL)
ON DUPLICATE KEY UPDATE id = id;

INSERT INTO assignment_rule (id, category, team_id) VALUES
    (1, 'Hardware', 1),
    (2, 'Software', 4),
    (3, 'Network',  2),
    (4, 'Database', 3),
    (5, 'Access',   1),
    (6, 'Other',    1)
ON DUPLICATE KEY UPDATE id = id;
