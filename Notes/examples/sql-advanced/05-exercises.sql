SELECT '--- C1 CTE above department average';
WITH dept_avg AS (
  SELECT dept_id, AVG(salary) AS avg_salary FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id
)
SELECT e.name, e.salary, ROUND(a.avg_salary) AS dept_avg
FROM emp e JOIN dept_avg a ON a.dept_id = e.dept_id
WHERE e.salary > a.avg_salary ORDER BY e.id;
SELECT '--- T1 earn more than manager';
SELECT e.name AS employee, e.salary, m.name AS manager, m.salary AS manager_salary
FROM emp e JOIN emp m ON m.id = e.manager_id WHERE e.salary > m.salary;
SELECT '--- T2 above dept average (window)';
SELECT name, dept_id, salary FROM (
  SELECT name, dept_id, salary, AVG(salary) OVER (PARTITION BY dept_id) AS avg_salary FROM emp WHERE dept_id IS NOT NULL
) t WHERE salary > avg_salary ORDER BY dept_id, salary DESC;
SELECT '--- T3 second highest';
SELECT MAX(salary) AS second_highest FROM emp WHERE salary < (SELECT MAX(salary) FROM emp);
SELECT DISTINCT salary FROM (SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS r FROM emp) t WHERE r = 2;
SELECT '--- T4 never ordered';
SELECT e.name FROM emp e WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.emp_id = e.id) ORDER BY e.id;
SELECT '--- T5 share of month';
SELECT emp_id, EXTRACT(MONTH FROM placed) AS month, SUM(amount) AS total,
       ROUND(100.0 * SUM(amount) / SUM(SUM(amount)) OVER (PARTITION BY EXTRACT(MONTH FROM placed)), 1) AS pct_of_month
FROM orders GROUP BY emp_id, EXTRACT(MONTH FROM placed) ORDER BY month, emp_id;
SELECT '--- T6 chain upwards from Dev';
WITH RECURSIVE up AS (
  SELECT id, name, manager_id FROM emp WHERE name = 'Dev'
  UNION ALL
  SELECT e.id, e.name, e.manager_id FROM emp e JOIN up ON e.id = up.manager_id
)
SELECT name FROM up;
SELECT '--- F1 full join emulation';
SELECT d.name AS dept, e.name AS employee FROM dept d LEFT JOIN emp e ON e.dept_id = d.id
UNION
SELECT d.name, e.name FROM dept d RIGHT JOIN emp e ON e.dept_id = d.id ORDER BY 1, 2;
SELECT '--- D1 division';
SELECT 5 / 2 AS five_over_two, 5 / 2.0 AS with_decimal, 5 / NULLIF(0, 0) AS safe_divide;
