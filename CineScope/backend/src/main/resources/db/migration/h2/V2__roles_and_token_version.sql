-- V2 (H2, used by the automated tests): roles and token versions for the users table. Same change as the MySQL script of the
-- same name - see there for the explanation. Keep the two in step.

ALTER TABLE users ADD COLUMN role VARCHAR(10) NOT NULL DEFAULT 'USER';
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
