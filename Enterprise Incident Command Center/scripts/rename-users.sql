-- Teacher feedback (2026-11-27): log in with a person's email, not a role name. Run once on an EXISTING database
-- (a fresh database already gets these from data.sql). Passwords and ids are unchanged, so incidents, audit and
-- every other record keep pointing to the same people.
USE icc_assignment;

UPDATE app_user SET username = 'mohit@icc.com',   email = 'mohit@icc.com',   full_name = 'Mohit Bali'          WHERE id = 1;
UPDATE app_user SET username = 'priya@icc.com',   email = 'priya@icc.com',   full_name = 'Priya Nair'          WHERE id = 2;
UPDATE app_user SET username = 'sai@icc.com',     email = 'sai@icc.com',     full_name = 'Sai Vivek'           WHERE id = 3;
UPDATE app_user SET username = 'sachin@icc.com',  email = 'sachin@icc.com',  full_name = 'Sachin Suryavanshi'  WHERE id = 4;
UPDATE app_user SET username = 'vinod@icc.com',   email = 'vinod@icc.com',   full_name = 'Vinod'               WHERE id = 5;
UPDATE app_user SET username = 'ananya@icc.com',  email = 'ananya@icc.com',  full_name = 'Ananya Iyer'         WHERE id = 6;
UPDATE app_user SET username = 'rohan@icc.com',   email = 'rohan@icc.com',   full_name = 'Rohan Mehta'         WHERE id = 7;

SELECT id, username, full_name, role, team_id FROM app_user ORDER BY id;
