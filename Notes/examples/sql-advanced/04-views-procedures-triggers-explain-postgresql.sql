DROP MATERIALIZED VIEW IF EXISTS dept_payroll_mv;
DROP VIEW IF EXISTS dept_payroll;
DROP TABLE IF EXISTS salary_audit;
DROP TRIGGER IF EXISTS emp_salary_audit ON emp;
DROP FUNCTION IF EXISTS emp_salary_audit_fn();
DROP FUNCTION IF EXISTS raise_salary(INT, NUMERIC);
DROP FUNCTION IF EXISTS grade_of(INT);

\echo --- V1 view and materialized view
CREATE VIEW dept_payroll AS
  SELECT d.id AS dept_id, d.name AS dept, COUNT(e.id) AS headcount, COALESCE(SUM(e.salary), 0) AS payroll
  FROM dept d LEFT JOIN emp e ON e.dept_id = d.id GROUP BY d.id, d.name;
CREATE MATERIALIZED VIEW dept_payroll_mv AS SELECT * FROM dept_payroll;
UPDATE emp SET salary = salary + 1000 WHERE id = 8;
SELECT 'live view' AS source, payroll FROM dept_payroll WHERE dept_id = 3
UNION ALL
SELECT 'materialized (stale)', payroll FROM dept_payroll_mv WHERE dept_id = 3;
REFRESH MATERIALIZED VIEW dept_payroll_mv;
SELECT 'materialized (refreshed)' AS source, payroll FROM dept_payroll_mv WHERE dept_id = 3;
UPDATE emp SET salary = 70000 WHERE id = 8;

CREATE TABLE salary_audit (
  id         SERIAL PRIMARY KEY,
  emp_id     INT NOT NULL,
  old_salary INT NOT NULL,
  new_salary INT NOT NULL,
  changed_by TEXT NOT NULL
);

CREATE FUNCTION raise_salary(p_dept INT, p_percent NUMERIC) RETURNS INT
LANGUAGE plpgsql AS $$
DECLARE changed INT;
BEGIN
  IF p_percent <= 0 OR p_percent > 50 THEN
    RAISE EXCEPTION 'percent must be between 0 and 50';
  END IF;
  UPDATE emp SET salary = ROUND(salary * (1 + p_percent / 100)) WHERE dept_id = p_dept;
  GET DIAGNOSTICS changed = ROW_COUNT;
  RETURN changed;
END $$;

CREATE FUNCTION grade_of(p_salary INT) RETURNS TEXT
LANGUAGE sql IMMUTABLE AS $$
  SELECT CASE WHEN p_salary >= 120000 THEN 'senior' WHEN p_salary >= 80000 THEN 'mid' ELSE 'junior' END
$$;

CREATE FUNCTION emp_salary_audit_fn() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.salary <> OLD.salary THEN
    INSERT INTO salary_audit (emp_id, old_salary, new_salary, changed_by) VALUES (OLD.id, OLD.salary, NEW.salary, CURRENT_USER);
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER emp_salary_audit AFTER UPDATE ON emp FOR EACH ROW EXECUTE FUNCTION emp_salary_audit_fn();

\echo --- P1 call, grade, audit
BEGIN;
SELECT raise_salary(3, 10) AS rows_changed;
SELECT id, name, salary, grade_of(salary) AS grade FROM emp WHERE dept_id = 3;
SELECT emp_id, old_salary, new_salary FROM salary_audit;
\echo --- P3 bad percent aborts the transaction
SELECT raise_salary(3, 80);
SELECT 1;
ROLLBACK;
SELECT COUNT(*) AS audit_rows_after_rollback, (SELECT salary FROM emp WHERE id = 8) AS hari_salary FROM salary_audit;

\echo --- N1 NULL ordering differs: PostgreSQL puts NULLs LAST for ASC, MySQL puts them FIRST
SELECT name, manager_id FROM emp ORDER BY manager_id, id LIMIT 3;
SELECT name, manager_id FROM emp ORDER BY manager_id NULLS FIRST, id LIMIT 3;

\echo --- E1 EXPLAIN in PostgreSQL
CREATE TABLE big (id INT PRIMARY KEY, email VARCHAR(60) NOT NULL, status SMALLINT NOT NULL, created DATE NOT NULL);
INSERT INTO big SELECT i, 'user' || i || '@example.com', i % 3, DATE '2024-01-01' + (i % 700) FROM generate_series(1, 20000) AS i;
ANALYZE big;
EXPLAIN SELECT * FROM big WHERE email = 'user777@example.com';
CREATE INDEX idx_big_email ON big (email);
EXPLAIN SELECT * FROM big WHERE email = 'user777@example.com';
EXPLAIN SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';
CREATE INDEX idx_big_upper_email ON big (UPPER(email));
EXPLAIN SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';
DROP TABLE big;
