SELECT '--- T7 longest run';
SELECT MAX(days_in_row) AS longest FROM (
  SELECT COUNT(*) AS days_in_row FROM (
    SELECT day, DATE_SUB(day, INTERVAL ROW_NUMBER() OVER (ORDER BY day) DAY) AS grp FROM visits
  ) t GROUP BY grp
) s;

SELECT '--- T8 archive trigger';
DROP TABLE IF EXISTS emp_archive;
DROP TRIGGER IF EXISTS emp_before_delete;
CREATE TABLE emp_archive LIKE emp;
ALTER TABLE emp_archive DROP FOREIGN KEY emp_archive_ibfk_1;
ALTER TABLE emp_archive DROP FOREIGN KEY emp_archive_ibfk_2;
CREATE TRIGGER emp_before_delete BEFORE DELETE ON emp FOR EACH ROW
  INSERT INTO emp_archive (id, name, dept_id, manager_id, salary, hired) VALUES (OLD.id, OLD.name, OLD.dept_id, OLD.manager_id, OLD.salary, OLD.hired);
START TRANSACTION;
INSERT INTO emp VALUES (20, 'Temp', 3, 1, 50000, '2026-01-01');
DELETE FROM emp WHERE id = 20;
SELECT id, name FROM emp_archive;
ROLLBACK;
DROP TRIGGER emp_before_delete;
DROP TABLE emp_archive;

SELECT '--- T9 functional index';
DROP TABLE IF EXISTS big;
CREATE TABLE big (id INT PRIMARY KEY, email VARCHAR(60) NOT NULL);
SET SESSION cte_max_recursion_depth = 30000;
INSERT INTO big WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 20000) SELECT i, CONCAT('user', i, '@example.com') FROM n;
ANALYZE TABLE big;
EXPLAIN SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';
CREATE INDEX idx_big_upper_email ON big ((UPPER(email)));
EXPLAIN SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';
DROP TABLE big;
SELECT '--- G1 GROUPING and PG FILTER';
SELECT dept_id, COUNT(*) AS n, GROUPING(dept_id) AS is_total FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id WITH ROLLUP;
