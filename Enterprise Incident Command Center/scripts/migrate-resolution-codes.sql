-- One-off conversion of an EXISTING icc_incident database to the fixed resolution codes (D36). Run once.
-- A fresh database already gets valid codes from demo-data.sql. Only the known old texts are changed; anything else is left as it is.
USE icc_incident;

UPDATE incident SET resolution_code = 'Solution provided'   WHERE resolution_code IN ('Fixed', 'Solved', 'Solved (Permanently)', 'Solved Remotely (Permanently)', 'RES001', 'RES002');
UPDATE incident SET resolution_code = 'Workaround provided' WHERE resolution_code IN ('Workaround', 'Solved (Work Around)', 'Solved Remotely (Work Around)');
UPDATE incident SET resolution_code = 'Resolved by caller'  WHERE resolution_code IN ('Closed/Resolved by Caller');

SELECT resolution_code, COUNT(*) AS incidents FROM incident WHERE resolution_code IS NOT NULL GROUP BY resolution_code;
