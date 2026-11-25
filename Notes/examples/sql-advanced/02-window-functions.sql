SELECT '--- W1 ranking functions (ties!)';
SELECT name, dept_id, salary,
       ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS row_num,
       RANK()       OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rnk,
       DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS dense_rnk
FROM emp WHERE dept_id IN (1, 2) ORDER BY dept_id, salary DESC, id;
SELECT '--- W2 top 1 per group (highest paid in each department)';
SELECT dept_id, name, salary FROM (
  SELECT dept_id, name, salary, ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC, id) AS rn
  FROM emp WHERE dept_id IS NOT NULL
) t WHERE rn = 1 ORDER BY dept_id;
SELECT '--- W3 salary vs department average and share';
SELECT name, dept_id, salary,
       ROUND(AVG(salary) OVER (PARTITION BY dept_id), 0) AS dept_avg,
       salary - ROUND(AVG(salary) OVER (PARTITION BY dept_id), 0) AS diff,
       ROUND(100.0 * salary / SUM(salary) OVER (PARTITION BY dept_id), 1) AS pct_of_dept
FROM emp WHERE dept_id = 2 ORDER BY id;
SELECT '--- W4 running total per employee, LAG and LEAD';
SELECT emp_id, placed, amount,
       SUM(amount) OVER (PARTITION BY emp_id ORDER BY placed) AS running_total,
       LAG(amount)  OVER (PARTITION BY emp_id ORDER BY placed) AS previous_amount,
       LEAD(amount) OVER (PARTITION BY emp_id ORDER BY placed) AS next_amount
FROM orders WHERE emp_id = 5 ORDER BY placed;
SELECT '--- W5 moving average of the last 2 rows (ROWS frame)';
SELECT placed, amount,
       ROUND(AVG(amount) OVER (ORDER BY placed ROWS BETWEEN 1 PRECEDING AND CURRENT ROW), 1) AS moving_avg_2
FROM orders ORDER BY placed;
SELECT '--- W6 default frame trap with LAST_VALUE';
SELECT placed, amount,
       LAST_VALUE(amount) OVER (ORDER BY placed) AS last_default_frame,
       LAST_VALUE(amount) OVER (ORDER BY placed ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS last_whole_partition
FROM orders WHERE emp_id = 6 ORDER BY placed;
SELECT '--- W7 NTILE quartiles';
SELECT name, salary, NTILE(3) OVER (ORDER BY salary DESC, id) AS tier FROM emp ORDER BY salary DESC, id;
SELECT '--- W8 de-duplicate: keep the newest order per employee';
SELECT emp_id, id AS order_id, placed FROM (
  SELECT o.*, ROW_NUMBER() OVER (PARTITION BY emp_id ORDER BY placed DESC) AS rn FROM orders o
) t WHERE rn = 1 ORDER BY emp_id;
SELECT '--- W9 RANGE vs ROWS with ties (two salaries of 120000)';
SELECT name, salary,
       SUM(salary) OVER (ORDER BY salary ROWS  BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS rows_running,
       SUM(salary) OVER (ORDER BY salary RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS range_running
FROM emp WHERE dept_id = 1 ORDER BY salary, id;
SELECT '--- W11 cumulative distribution and percent rank';
SELECT name, salary, ROUND(CAST(PERCENT_RANK() OVER (ORDER BY salary) AS DECIMAL(10,4)), 3) AS pct_rank, ROUND(CAST(CUME_DIST() OVER (ORDER BY salary) AS DECIMAL(10,4)), 3) AS cume FROM emp ORDER BY salary, id;
