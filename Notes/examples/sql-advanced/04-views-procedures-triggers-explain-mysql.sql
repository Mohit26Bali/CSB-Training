DROP VIEW IF EXISTS sales_staff;
DROP VIEW IF EXISTS dept_payroll;
DROP TABLE IF EXISTS salary_audit;
DROP TRIGGER IF EXISTS emp_salary_check;
DROP TRIGGER IF EXISTS emp_salary_audit;
DROP PROCEDURE IF EXISTS raise_salary;
DROP FUNCTION IF EXISTS grade_of;

SELECT '--- V1 a view hides a query';
CREATE VIEW dept_payroll AS
  SELECT d.id AS dept_id, d.name AS dept, COUNT(e.id) AS headcount, COALESCE(SUM(e.salary), 0) AS payroll
  FROM dept d LEFT JOIN emp e ON e.dept_id = d.id GROUP BY d.id, d.name;
SELECT * FROM dept_payroll ORDER BY dept_id;

SELECT '--- V2 updatable view with CHECK OPTION';
CREATE VIEW sales_staff AS SELECT id, name, dept_id, salary, hired FROM emp WHERE dept_id = 2 WITH CHECK OPTION;
INSERT INTO sales_staff (id, name, dept_id, salary, hired) VALUES (10, 'Jay', 2, 60000, '2026-01-05');
SELECT COUNT(*) AS sales_rows_now FROM sales_staff;
INSERT INTO sales_staff (id, name, dept_id, salary, hired) VALUES (11, 'Kim', 1, 60000, '2026-01-05');
SELECT COUNT(*) AS emp_rows_after_rejected_insert FROM emp;
DELETE FROM emp WHERE id = 10;

CREATE TABLE salary_audit (
  id         INT AUTO_INCREMENT PRIMARY KEY,
  emp_id     INT NOT NULL,
  old_salary INT NOT NULL,
  new_salary INT NOT NULL,
  changed_by VARCHAR(64) NOT NULL
);

DELIMITER //
CREATE PROCEDURE raise_salary(IN p_dept INT, IN p_percent DECIMAL(5,2), OUT p_changed INT)
BEGIN
  IF p_percent <= 0 OR p_percent > 50 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'percent must be between 0 and 50';
  END IF;
  UPDATE emp SET salary = ROUND(salary * (1 + p_percent / 100)) WHERE dept_id = p_dept;
  SET p_changed = ROW_COUNT();
END //

CREATE FUNCTION grade_of(p_salary INT) RETURNS VARCHAR(10) DETERMINISTIC
BEGIN
  RETURN CASE WHEN p_salary >= 120000 THEN 'senior' WHEN p_salary >= 80000 THEN 'mid' ELSE 'junior' END;
END //

CREATE TRIGGER emp_salary_check BEFORE UPDATE ON emp FOR EACH ROW
BEGIN
  IF NEW.salary < 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'salary cannot be negative';
  END IF;
END //

CREATE TRIGGER emp_salary_audit AFTER UPDATE ON emp FOR EACH ROW
BEGIN
  IF NEW.salary <> OLD.salary THEN
    INSERT INTO salary_audit (emp_id, old_salary, new_salary, changed_by) VALUES (OLD.id, OLD.salary, NEW.salary, CURRENT_USER());
  END IF;
END //
DELIMITER ;

SELECT '--- P1 call the procedure and the function';
START TRANSACTION;
CALL raise_salary(3, 10, @changed);
SELECT @changed AS rows_changed;
SELECT id, name, salary, grade_of(salary) AS grade FROM emp WHERE dept_id = 3;
SELECT '--- P2 the trigger wrote an audit row';
SELECT emp_id, old_salary, new_salary FROM salary_audit;
SELECT '--- P3 the procedure refuses a bad percent';
CALL raise_salary(3, 80, @changed);
SELECT '--- P4 the BEFORE trigger refuses a negative salary';
UPDATE emp SET salary = -1 WHERE id = 8;
ROLLBACK;
SELECT '--- P5 after ROLLBACK the audit row is gone too (triggers run in the same transaction)';
SELECT COUNT(*) AS audit_rows, (SELECT salary FROM emp WHERE id = 8) AS hari_salary FROM salary_audit;

SELECT '--- E1 EXPLAIN: index use, function on a column, wildcards';
DROP TABLE IF EXISTS big;
CREATE TABLE big (id INT PRIMARY KEY, email VARCHAR(60) NOT NULL, status TINYINT NOT NULL, created DATE NOT NULL);
SET SESSION cte_max_recursion_depth = 30000;
INSERT INTO big
  WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 20000)
  SELECT i, CONCAT('user', i, '@example.com'), i % 3, DATE_ADD('2024-01-01', INTERVAL i % 700 DAY) FROM n;
ANALYZE TABLE big;
EXPLAIN SELECT * FROM big WHERE email = 'user777@example.com';
CREATE INDEX idx_big_email ON big (email);
EXPLAIN SELECT * FROM big WHERE email = 'user777@example.com';
EXPLAIN SELECT * FROM big WHERE email LIKE 'user77%';
EXPLAIN SELECT * FROM big WHERE email LIKE '%777@example.com';
EXPLAIN SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';
CREATE INDEX idx_big_status_created ON big (status, created);
EXPLAIN SELECT status, created FROM big WHERE status = 1 AND created >= '2025-01-01';
EXPLAIN SELECT * FROM big WHERE created >= '2025-01-01';
DROP TABLE big;
