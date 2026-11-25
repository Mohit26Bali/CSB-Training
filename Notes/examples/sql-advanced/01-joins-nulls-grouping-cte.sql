SELECT '--- 1 inner join';
SELECT e.name, d.name AS dept FROM emp e JOIN dept d ON d.id = e.dept_id ORDER BY e.id;
SELECT '--- 2 left join keeps Isha';
SELECT e.name, d.name AS dept FROM emp e LEFT JOIN dept d ON d.id = e.dept_id ORDER BY e.id;
SELECT '--- 3 right join keeps Legal';
SELECT d.name AS dept, e.name FROM emp e RIGHT JOIN dept d ON d.id = e.dept_id ORDER BY d.id, e.id;
SELECT '--- 4 departments with no employees (anti join: LEFT JOIN IS NULL)';
SELECT d.name FROM dept d LEFT JOIN emp e ON e.dept_id = d.id WHERE e.id IS NULL;
SELECT '--- 5 same with NOT EXISTS';
SELECT d.name FROM dept d WHERE NOT EXISTS (SELECT 1 FROM emp e WHERE e.dept_id = d.id);
SELECT '--- 6 NOT IN trap: subquery returns a NULL (Isha has NULL dept_id)';
SELECT d.name FROM dept d WHERE d.id NOT IN (SELECT dept_id FROM emp);
SELECT '--- 7 NOT IN fixed by filtering NULL';
SELECT d.name FROM dept d WHERE d.id NOT IN (SELECT dept_id FROM emp WHERE dept_id IS NOT NULL);
SELECT '--- 8 self join: employee and manager';
SELECT e.name AS employee, m.name AS manager FROM emp e LEFT JOIN emp m ON m.id = e.manager_id ORDER BY e.id;
SELECT '--- 9 cross join count';
SELECT COUNT(*) AS combos FROM dept CROSS JOIN (SELECT 1 AS x UNION ALL SELECT 2 UNION ALL SELECT 3) t;
SELECT '--- 10 fan-out: sum of salaries joined with orders is WRONG';
SELECT SUM(e.salary) AS wrong_total FROM emp e JOIN orders o ON o.emp_id = e.id WHERE e.dept_id = 2;
SELECT SUM(salary) AS right_total FROM emp WHERE dept_id = 2;
SELECT '--- 11 ON vs WHERE in a LEFT JOIN';
SELECT d.name, COUNT(e.id) AS emps_over_100k FROM dept d LEFT JOIN emp e ON e.dept_id = d.id AND e.salary > 100000 GROUP BY d.id, d.name ORDER BY d.id;
SELECT d.name, COUNT(e.id) AS emps_over_100k FROM dept d LEFT JOIN emp e ON e.dept_id = d.id WHERE e.salary > 100000 GROUP BY d.id, d.name ORDER BY d.id;
SELECT '--- 12 group by having';
SELECT dept_id, COUNT(*) AS n, AVG(salary) AS avg_salary, MAX(salary) AS top FROM emp GROUP BY dept_id HAVING COUNT(*) >= 2 ORDER BY dept_id;
SELECT '--- 13 conditional aggregation';
SELECT dept_id, SUM(CASE WHEN salary >= 100000 THEN 1 ELSE 0 END) AS high, SUM(CASE WHEN salary < 100000 THEN 1 ELSE 0 END) AS low FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id ORDER BY dept_id;
SELECT '--- 14 aggregates ignore NULL; COUNT(*) vs COUNT(col)';
SELECT COUNT(*) AS all_rows, COUNT(dept_id) AS with_dept, COUNT(DISTINCT dept_id) AS distinct_depts, AVG(manager_id) AS avg_mgr FROM emp;
SELECT '--- 15 NULL logic';
SELECT 1 = NULL AS eq_null, NULL = NULL AS null_eq_null, NULL IS NULL AS is_null, COALESCE(NULL, 5) AS coalesce_v, NULLIF(5, 5) AS nullif_v;
SELECT '--- 16 recursive CTE: org chart';
WITH RECURSIVE chain AS (
  SELECT id, name, manager_id, 1 AS level FROM emp WHERE manager_id IS NULL
  UNION ALL
  SELECT e.id, e.name, e.manager_id, c.level + 1 FROM emp e JOIN chain c ON e.manager_id = c.id
)
SELECT id, name, level FROM chain ORDER BY level, id;
SELECT '--- 17 recursive CTE: number series';
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 5)
SELECT i FROM n;
SELECT '--- 18 set operations';
SELECT id FROM dept WHERE id <= 3 UNION SELECT id FROM dept WHERE id >= 3 ORDER BY id;
SELECT id FROM dept WHERE id <= 3 UNION ALL SELECT id FROM dept WHERE id >= 3 ORDER BY id;
SELECT id FROM dept WHERE id <= 3 INTERSECT SELECT id FROM dept WHERE id >= 3;
SELECT id FROM dept WHERE id <= 3 EXCEPT SELECT id FROM dept WHERE id >= 3 ORDER BY id;
