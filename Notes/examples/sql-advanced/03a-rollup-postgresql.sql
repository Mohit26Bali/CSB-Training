SELECT '--- R';
SELECT dept_id, COUNT(*) AS n, SUM(salary) AS payroll FROM emp WHERE dept_id IS NOT NULL GROUP BY ROLLUP (dept_id) ORDER BY dept_id NULLS LAST;
SELECT EXTRACT(MONTH FROM placed) AS month, SUM(amount) AS total FROM orders GROUP BY EXTRACT(MONTH FROM placed) ORDER BY month;
