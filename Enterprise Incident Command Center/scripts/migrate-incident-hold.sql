-- One-off migration of an EXISTING icc_incident database for On Hold and subcategory (D32). Run once.
-- A fresh database does not need it: schema.sql already creates the new shape.
-- The state column is VARCHAR(20), which already fits ON_HOLD, so only two columns are added.

USE icc_incident;

ALTER TABLE incident
    ADD COLUMN subcategory VARCHAR(50) AFTER category,
    ADD COLUMN hold_reason VARCHAR(20) AFTER assigned_to_id;

SELECT id, number, category, subcategory, state, hold_reason FROM incident ORDER BY id LIMIT 5;
