# SQL Advanced: Joins in Depth, CTEs, Window Functions, Views, Procedures, Triggers, Reading Query Plans and NoSQL

The second half of SQL: the join and `NULL` traps that return wrong answers silently, `GROUP BY` and rollups, **common table expressions** (including recursive ones), **window functions** (ranking, running totals, "top N per group"), views, stored procedures, functions and triggers, how to read **`EXPLAIN`** to see whether an index is used, and an honest introduction to **NoSQL**.

> **Everything here was run.** The queries were executed on a real **MySQL 8.4** server and a real **PostgreSQL 17** server using the data in this note, and the results shown are the actual results. Where the two databases differ, both forms are given. The basics (`SELECT`, `INSERT`, constraints, normalization, transactions, locks) are in the *SQL, Transactions, Locks and Cascade* notes. The scripts are in `examples/sql-advanced/`.

---

## Table of Contents

1. [The Sample Data](#1-the-sample-data)
2. [Joins in Depth](#2-joins-in-depth)
3. [Anti-Joins, `NOT IN` and the `NULL` Trap](#3-anti-joins-not-in-and-the-null-trap)
4. [`NULL` Logic](#4-null-logic)
5. [Grouping, `HAVING`, Conditional Aggregation and Rollups](#5-grouping-having-conditional-aggregation-and-rollups)
6. [Subqueries, CTEs and Recursive CTEs](#6-subqueries-ctes-and-recursive-ctes)
7. [Set Operations](#7-set-operations)
8. [Window Functions](#8-window-functions)
9. [Views](#9-views)
10. [Stored Procedures and Functions](#10-stored-procedures-and-functions)
11. [Triggers](#11-triggers)
12. [Reading `EXPLAIN` and Writing Index-Friendly SQL](#12-reading-explain-and-writing-index-friendly-sql)
13. [MySQL and PostgreSQL Differences at a Glance](#13-mysql-and-postgresql-differences-at-a-glance)
14. [NoSQL: What, Why and When](#14-nosql-what-why-and-when)
15. [Common Mistakes](#15-common-mistakes)
16. [Output Prediction Puzzles](#16-output-prediction-puzzles)
17. [Interview Questions](#17-interview-questions)
18. [Cheat Sheet](#18-cheat-sheet)
19. [Practice Tasks with Solutions](#19-practice-tasks-with-solutions)

---

## 1. The Sample Data

```sql
CREATE TABLE dept (id INT PRIMARY KEY, name VARCHAR(30) NOT NULL);

CREATE TABLE emp (
  id INT PRIMARY KEY, name VARCHAR(30) NOT NULL,
  dept_id INT NULL, manager_id INT NULL,
  salary INT NOT NULL, hired DATE NOT NULL,
  FOREIGN KEY (dept_id) REFERENCES dept(id),
  FOREIGN KEY (manager_id) REFERENCES emp(id));

CREATE TABLE orders (id INT PRIMARY KEY, emp_id INT NOT NULL, amount INT NOT NULL, placed DATE NOT NULL,
  FOREIGN KEY (emp_id) REFERENCES emp(id));

CREATE TABLE visits (day DATE PRIMARY KEY);
```

| dept | | emp | name | dept | manager | salary | hired |
|---|---|---|---|---|---|---|---|
| 1 Engineering | | 1 | Asha | 1 | NULL | 150000 | 2018-03-01 |
| 2 Sales | | 2 | Ben | 1 | 1 | 120000 | 2019-06-15 |
| 3 HR | | 3 | Chitra | 1 | 1 | 120000 | 2020-01-10 |
| 4 Legal (no employees) | | 4 | Dev | 1 | 2 | 90000 | 2021-09-01 |
| | | 5 | Esha | 2 | 1 | 110000 | 2019-02-20 |
| | | 6 | Farid | 2 | 5 | 80000 | 2022-04-05 |
| | | 7 | Gita | 2 | 5 | 80000 | 2023-07-17 |
| | | 8 | Hari | 3 | 1 | 70000 | 2020-11-30 |
| | | 9 | **Isha** | **NULL** | 1 | 65000 | 2024-02-12 |

`orders` has nine rows for employees 5, 6 and 7 (Sales): Esha 500, 700, 1200, 150; Farid 300, 450, 800; Gita 900, 250. `visits` holds the days 2026-03-01, 02, 03, 06, 07 and 10.

Two things are deliberate: **Isha has no department** (`dept_id` is `NULL`) and **Legal has no employees**. They expose most of the join and `NULL` traps below.

---

## 2. Joins in Depth

A join combines rows from two tables where a condition holds. The type decides what happens to rows with **no match**.

| Join | Keeps |
|---|---|
| `INNER JOIN` (`JOIN`) | only rows that match on both sides |
| `LEFT JOIN` | **all** left rows; right columns are `NULL` when there is no match |
| `RIGHT JOIN` | all right rows (rarely needed: swap the tables and use `LEFT`) |
| `FULL JOIN` | all rows from both sides (PostgreSQL yes; **MySQL has none**: use `LEFT JOIN ... UNION ... RIGHT JOIN`) |
| `CROSS JOIN` | every combination (rows A × rows B); no condition |

**Inner join** drops Isha (no department):

```sql
SELECT e.name, d.name AS dept FROM emp e JOIN dept d ON d.id = e.dept_id ORDER BY e.id;
-- 8 rows: Asha..Gita, Hari. Isha is missing.
```

**Left join** keeps her, with `NULL`:

```sql
SELECT e.name, d.name AS dept FROM emp e LEFT JOIN dept d ON d.id = e.dept_id ORDER BY e.id;
-- ... Hari | HR
-- ... Isha | NULL
```

**Right join** keeps the empty department:

```sql
SELECT d.name AS dept, e.name FROM emp e RIGHT JOIN dept d ON d.id = e.dept_id ORDER BY d.id, e.id;
-- ... HR | Hari
-- ... Legal | NULL
```

**Self join**: a table joined to itself, for hierarchies such as employee and manager:

```sql
SELECT e.name AS employee, m.name AS manager
FROM emp e LEFT JOIN emp m ON m.id = e.manager_id ORDER BY e.id;
```

| employee | manager |
|---|---|
| Asha | NULL |
| Ben | Asha |
| Chitra | Asha |
| Dev | Ben |
| Esha | Asha |
| Farid | Esha |
| Gita | Esha |
| Hari | Asha |
| Isha | Asha |

**Cross join** gives `rows(A) × rows(B)`: `dept CROSS JOIN` three numbers returns **12** rows. Useful for generating combinations (every employee × every month); dangerous when forgotten (a missing join condition silently produces a cross join and millions of rows).

### `ON` versus `WHERE` in a `LEFT JOIN`

A condition on the **right** table in `WHERE` turns the left join into an inner join:

```sql
-- keeps every department, counting only employees over 100000
SELECT d.name, COUNT(e.id) AS emps_over_100k
FROM dept d LEFT JOIN emp e ON e.dept_id = d.id AND e.salary > 100000
GROUP BY d.id, d.name ORDER BY d.id;
```

| name | emps_over_100k |
|---|---|
| Engineering | 3 |
| Sales | 1 |
| HR | 0 |
| Legal | 0 |

```sql
-- the same condition in WHERE: HR and Legal DISAPPEAR
SELECT d.name, COUNT(e.id) AS emps_over_100k
FROM dept d LEFT JOIN emp e ON e.dept_id = d.id
WHERE e.salary > 100000 GROUP BY d.id, d.name ORDER BY d.id;
```

| name | emps_over_100k |
|---|---|
| Engineering | 3 |
| Sales | 1 |

`WHERE` runs **after** the join and throws away the `NULL` rows that the left join created. **Rule:** conditions that describe *which right rows may match* go in `ON`; conditions that filter the final result go in `WHERE`.

### The fan-out trap (duplicated rows inflate sums)

A one-to-many join repeats the "one" side once per matching "many" row.

```sql
-- total salary of Sales, joined with their orders: WRONG
SELECT SUM(e.salary) AS wrong_total FROM emp e JOIN orders o ON o.emp_id = e.id WHERE e.dept_id = 2;   -- 840000

SELECT SUM(salary) AS right_total FROM emp WHERE dept_id = 2;                                          -- 270000
```

Esha's salary is counted four times (four orders), Farid's three, Gita's twice: `110000×4 + 80000×3 + 80000×2 = 840000`. **Aggregate each table to the right level first** (a subquery or CTE per table), then join the results, or use `COUNT(DISTINCT ...)` carefully.

---

## 3. Anti-Joins, `NOT IN` and the `NULL` Trap

"Departments with no employees" is an **anti-join**: rows with *no* match. There are three ways, and one of them is dangerous.

```sql
-- 1. LEFT JOIN ... IS NULL
SELECT d.name FROM dept d LEFT JOIN emp e ON e.dept_id = d.id WHERE e.id IS NULL;          -- Legal

-- 2. NOT EXISTS   (recommended: clear and NULL-safe)
SELECT d.name FROM dept d WHERE NOT EXISTS (SELECT 1 FROM emp e WHERE e.dept_id = d.id);   -- Legal

-- 3. NOT IN   (dangerous with NULLs)
SELECT d.name FROM dept d WHERE d.id NOT IN (SELECT dept_id FROM emp);                     -- (no rows!)
```

The third returns **nothing**, in both MySQL and PostgreSQL, because Isha's `dept_id` is `NULL`. `4 NOT IN (1, 2, 3, NULL)` means `4 <> 1 AND 4 <> 2 AND 4 <> 3 AND 4 <> NULL`; the last comparison is `UNKNOWN`, so the whole condition is never true.

```sql
SELECT d.name FROM dept d WHERE d.id NOT IN (SELECT dept_id FROM emp WHERE dept_id IS NOT NULL);   -- Legal
```

> **Rule:** never use `NOT IN (subquery)` on a column that can be `NULL`. Use `NOT EXISTS`.

`EXISTS` (the matching "semi-join") stops at the first match, so it is also the efficient way to ask "has at least one": `WHERE EXISTS (SELECT 1 FROM orders o WHERE o.emp_id = e.id)`. `IN` and `EXISTS` give the same answer for non-null data; the optimizers usually plan them the same way.

---

## 4. `NULL` Logic

`NULL` means "unknown", not zero or empty. SQL uses **three-valued logic**: `TRUE`, `FALSE`, `UNKNOWN`. A `WHERE` keeps only rows where the condition is `TRUE`.

```sql
SELECT 1 = NULL AS eq_null, NULL = NULL AS null_eq_null, NULL IS NULL AS is_null,
       COALESCE(NULL, 5) AS coalesce_v, NULLIF(5, 5) AS nullif_v;
```

| eq_null | null_eq_null | is_null | coalesce_v | nullif_v |
|---|---|---|---|---|
| NULL | NULL | 1 (true) | 5 | NULL |

| Expression | Result |
|---|---|
| `x = NULL`, `x <> NULL` | always `UNKNOWN`: use `IS NULL` / `IS NOT NULL` |
| `NULL AND FALSE` | `FALSE` |
| `NULL OR TRUE` | `TRUE` |
| `NOT UNKNOWN` | `UNKNOWN` |
| `NULL + 5`, `CONCAT` in PostgreSQL `'a' \|\| NULL` | `NULL` |
| `COALESCE(a, b, ...)` | the first non-`NULL` |
| `NULLIF(a, b)` | `NULL` when `a = b`, else `a` (avoids divide-by-zero: `x / NULLIF(y, 0)`) |

**Aggregates ignore `NULL`** (except `COUNT(*)`):

```sql
SELECT COUNT(*) AS all_rows, COUNT(dept_id) AS with_dept, COUNT(DISTINCT dept_id) AS distinct_depts, AVG(manager_id) AS avg_mgr FROM emp;
```

| all_rows | with_dept | distinct_depts | avg_mgr |
|---|---|---|---|
| 9 | 8 | 3 | 2.125 |

`AVG(manager_id)` is the average of the **eight** non-null values (17 / 8), not nine. `SUM` of nothing is `NULL`, so wrap with `COALESCE(SUM(x), 0)`.

### Sorting with `NULL` differs by database

```sql
SELECT name, manager_id FROM emp ORDER BY manager_id, id LIMIT 3;
```

| MySQL (NULLs first in ASC) | PostgreSQL (NULLs last in ASC) |
|---|---|
| Asha (NULL), Ben (1), Chitra (1) | Ben (1), Chitra (1), Esha (1) |

PostgreSQL lets you choose: `ORDER BY manager_id NULLS FIRST`. In MySQL write `ORDER BY manager_id IS NULL, manager_id`. Always state the order you need when `NULL`s are possible, and add `id` as a tie-breaker so paging is stable.

---

## 5. Grouping, `HAVING`, Conditional Aggregation and Rollups

`WHERE` filters rows **before** grouping; `HAVING` filters groups **after** aggregation (and may use aggregates).

```sql
SELECT dept_id, COUNT(*) AS n, AVG(salary) AS avg_salary, MAX(salary) AS top
FROM emp GROUP BY dept_id HAVING COUNT(*) >= 2 ORDER BY dept_id;
```

| dept_id | n | avg_salary | top |
|---|---|---|---|
| 1 | 4 | 120000 | 150000 |
| 2 | 3 | 90000 | 110000 |

Order of evaluation: `FROM`/`JOIN` → `WHERE` → `GROUP BY` → `HAVING` → `SELECT` (aliases created here) → `DISTINCT` → `ORDER BY` → `LIMIT`. That is why a `SELECT` alias cannot be used in `WHERE`.

Every selected column must be in `GROUP BY` or inside an aggregate. (MySQL's `ONLY_FULL_GROUP_BY` mode, on by default, enforces this; PostgreSQL always does.)

### Conditional aggregation: pivot with `CASE`

```sql
SELECT dept_id,
       SUM(CASE WHEN salary >= 100000 THEN 1 ELSE 0 END) AS high,
       SUM(CASE WHEN salary <  100000 THEN 1 ELSE 0 END) AS low
FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id ORDER BY dept_id;
```

| dept_id | high | low |
|---|---|---|
| 1 | 3 | 1 |
| 2 | 1 | 2 |
| 3 | 0 | 1 |

(PostgreSQL also has `COUNT(*) FILTER (WHERE salary >= 100000)`.)

### Subtotals with `ROLLUP`

| MySQL | PostgreSQL |
|---|---|
| `GROUP BY dept_id WITH ROLLUP` | `GROUP BY ROLLUP (dept_id)` |

```sql
SELECT dept_id, COUNT(*) AS n, SUM(salary) AS payroll
FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id WITH ROLLUP;
```

| dept_id | n | payroll |
|---|---|---|
| 1 | 4 | 480000 |
| 2 | 3 | 270000 |
| 3 | 1 | 70000 |
| NULL (the grand total) | 8 | 820000 |

The `NULL` row is the total; use `GROUPING(dept_id)` to tell it from a real `NULL` group. `GROUPING SETS` and `CUBE` (PostgreSQL) produce other combinations.

---

## 6. Subqueries, CTEs and Recursive CTEs

A **subquery** is a query inside another (in `WHERE`, `FROM` or `SELECT`). A **CTE** (common table expression, `WITH name AS (...)`) names a subquery so the main query reads top to bottom. Both MySQL 8+ and PostgreSQL support them.

```sql
WITH dept_avg AS (
  SELECT dept_id, AVG(salary) AS avg_salary FROM emp WHERE dept_id IS NOT NULL GROUP BY dept_id
)
SELECT e.name, e.salary, ROUND(a.avg_salary) AS dept_avg
FROM emp e JOIN dept_avg a ON a.dept_id = e.dept_id
WHERE e.salary > a.avg_salary;                         -- people paid above their department average
```

A **correlated subquery** refers to the outer row and is conceptually re-run per row: `WHERE salary > (SELECT AVG(salary) FROM emp x WHERE x.dept_id = e.dept_id)`. Often a join or window function is faster.

### Recursive CTEs: hierarchies and series

```sql
WITH RECURSIVE chain AS (
  SELECT id, name, manager_id, 1 AS level FROM emp WHERE manager_id IS NULL        -- anchor: the top
  UNION ALL
  SELECT e.id, e.name, e.manager_id, c.level + 1                                    -- recursive step
  FROM emp e JOIN chain c ON e.manager_id = c.id
)
SELECT id, name, level FROM chain ORDER BY level, id;
```

| id | name | level |
|---|---|---|
| 1 | Asha | 1 |
| 2, 3, 5, 8, 9 | Ben, Chitra, Esha, Hari, Isha | 2 |
| 4, 6, 7 | Dev, Farid, Gita | 3 |

A number series for free (handy for generating dates or test data):

```sql
WITH RECURSIVE n AS (SELECT 1 AS i UNION ALL SELECT i + 1 FROM n WHERE i < 5)
SELECT i FROM n;          -- 1 2 3 4 5
```

Always include a **stop condition** (`WHERE i < 5`) or the query loops until the server's limit (`cte_max_recursion_depth`, default 1000 in MySQL). For graphs that can contain cycles, track the path or depth.

---

## 7. Set Operations

Combine the results of two queries with the **same number and compatible types of columns**.

| Operator | Result |
|---|---|
| `UNION` | rows from both, **duplicates removed** (sorts or hashes: slower) |
| `UNION ALL` | rows from both, **duplicates kept** (fast; prefer when duplicates cannot occur or are wanted) |
| `INTERSECT` | rows present in both |
| `EXCEPT` (`MINUS` in Oracle) | rows in the first but not the second |

```sql
SELECT id FROM dept WHERE id <= 3 UNION     SELECT id FROM dept WHERE id >= 3;   -- 1 2 3 4
SELECT id FROM dept WHERE id <= 3 UNION ALL SELECT id FROM dept WHERE id >= 3;   -- 1 2 3 3 4
SELECT id FROM dept WHERE id <= 3 INTERSECT SELECT id FROM dept WHERE id >= 3;   -- 3
SELECT id FROM dept WHERE id <= 3 EXCEPT    SELECT id FROM dept WHERE id >= 3;   -- 1 2
```

(`INTERSECT` and `EXCEPT` need MySQL 8.0.31 or later.) `ORDER BY` goes once, at the end.

---

## 8. Window Functions

A **window function** computes a value for each row **using other rows**, without collapsing them like `GROUP BY` does. This is the most useful SQL feature added in the last decade; learn it well.

```
function(...) OVER ( PARTITION BY ...   -- split into groups (optional)
                     ORDER BY ...       -- order inside each group (optional)
                     ROWS/RANGE ... )   -- which rows around the current one to include (optional)
```

### Ranking: `ROW_NUMBER`, `RANK`, `DENSE_RANK`

```sql
SELECT name, dept_id, salary,
       ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS row_num,
       RANK()       OVER (PARTITION BY dept_id ORDER BY salary DESC) AS rnk,
       DENSE_RANK() OVER (PARTITION BY dept_id ORDER BY salary DESC) AS dense_rnk
FROM emp WHERE dept_id IN (1, 2) ORDER BY dept_id, salary DESC, id;
```

| name | dept | salary | row_num | rnk | dense_rnk |
|---|---|---|---|---|---|
| Asha | 1 | 150000 | 1 | 1 | 1 |
| Ben | 1 | 120000 | 2 | 2 | 2 |
| Chitra | 1 | 120000 | 3 | **2** | **2** |
| Dev | 1 | 90000 | 4 | **4** | **3** |
| Esha | 2 | 110000 | 1 | 1 | 1 |
| Farid | 2 | 80000 | 2 | 2 | 2 |
| Gita | 2 | 80000 | 3 | 2 | 2 |

| Function | Ties | Next value after a tie |
|---|---|---|
| `ROW_NUMBER` | broken arbitrarily (add a tie-break column to the `ORDER BY`) | always +1 |
| `RANK` | same rank | **skips** (1, 2, 2, **4**) |
| `DENSE_RANK` | same rank | no gaps (1, 2, 2, **3**) |
| `NTILE(n)` | splits into `n` buckets as equal as possible | |

### Top N per group (the classic)

```sql
SELECT dept_id, name, salary FROM (
  SELECT dept_id, name, salary,
         ROW_NUMBER() OVER (PARTITION BY dept_id ORDER BY salary DESC, id) AS rn
  FROM emp WHERE dept_id IS NOT NULL
) t WHERE rn = 1 ORDER BY dept_id;
```

| dept_id | name | salary |
|---|---|---|
| 1 | Asha | 150000 |
| 2 | Esha | 110000 |
| 3 | Hari | 70000 |

(Window functions cannot be used directly in `WHERE`; that is why the subquery is needed.) The same pattern **de-duplicates**: keep the newest order per employee with `ROW_NUMBER() OVER (PARTITION BY emp_id ORDER BY placed DESC)` and `rn = 1`.

### Aggregates as window functions: compare a row with its group

```sql
SELECT name, salary,
       ROUND(AVG(salary) OVER (PARTITION BY dept_id), 0) AS dept_avg,
       ROUND(100.0 * salary / SUM(salary) OVER (PARTITION BY dept_id), 1) AS pct_of_dept
FROM emp WHERE dept_id = 2 ORDER BY id;
```

| name | salary | dept_avg | pct_of_dept |
|---|---|---|---|
| Esha | 110000 | 90000 | 40.7 |
| Farid | 80000 | 90000 | 29.6 |
| Gita | 80000 | 90000 | 29.6 |

### Running totals, `LAG` and `LEAD`

```sql
SELECT emp_id, placed, amount,
       SUM(amount) OVER (PARTITION BY emp_id ORDER BY placed) AS running_total,
       LAG(amount)  OVER (PARTITION BY emp_id ORDER BY placed) AS previous_amount,
       LEAD(amount) OVER (PARTITION BY emp_id ORDER BY placed) AS next_amount
FROM orders WHERE emp_id = 5 ORDER BY placed;
```

| emp_id | placed | amount | running_total | previous_amount | next_amount |
|---|---|---|---|---|---|
| 5 | 2026-01-05 | 500 | 500 | NULL | 700 |
| 5 | 2026-01-20 | 700 | 1200 | 500 | 1200 |
| 5 | 2026-03-02 | 1200 | 2400 | 700 | 150 |
| 5 | 2026-04-01 | 150 | 2550 | 1200 | NULL |

`LAG(x, 2, 0)` looks two rows back with a default of `0` instead of `NULL`. Month-over-month change is `amount - LAG(amount) OVER (ORDER BY month)`.

### Frames: which rows does the function see?

A **frame** limits the window around the current row.

```sql
SELECT placed, amount,
       ROUND(AVG(amount) OVER (ORDER BY placed ROWS BETWEEN 1 PRECEDING AND CURRENT ROW), 1) AS moving_avg_2
FROM orders ORDER BY placed;
```

| placed | amount | moving_avg_2 |
|---|---|---|
| 2026-01-05 | 500 | 500.0 |
| 2026-01-20 | 700 | 600.0 |
| 2026-02-03 | 300 | 500.0 |
| 2026-02-18 | 450 | 375.0 |
| 2026-02-25 | 900 | 675.0 |
| ... | | |

Two traps:

1. **Default frame.** With an `ORDER BY` and no frame, the frame is `RANGE BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW`. So `LAST_VALUE` returns the **current** row, not the last of the group:

```sql
SELECT placed, amount,
       LAST_VALUE(amount) OVER (ORDER BY placed) AS last_default_frame,
       LAST_VALUE(amount) OVER (ORDER BY placed ROWS BETWEEN UNBOUNDED PRECEDING AND UNBOUNDED FOLLOWING) AS last_whole_partition
FROM orders WHERE emp_id = 6 ORDER BY placed;
```

| placed | amount | last_default_frame | last_whole_partition |
|---|---|---|---|
| 2026-02-03 | 300 | 300 | 800 |
| 2026-02-18 | 450 | 450 | 800 |
| 2026-03-28 | 800 | 800 | 800 |

2. **`ROWS` counts rows; `RANGE` includes all rows with the same `ORDER BY` value (peers).** With two salaries of 120000:

| name | salary | `ROWS` running sum | `RANGE` running sum |
|---|---|---|---|
| Dev | 90000 | 90000 | 90000 |
| Ben | 120000 | 210000 | **330000** |
| Chitra | 120000 | 330000 | **330000** |
| Asha | 150000 | 480000 | 480000 |

### Other useful functions

`NTILE(3)` (tiers), `PERCENT_RANK()`, `CUME_DIST()`, `FIRST_VALUE`, `NTH_VALUE`. For salaries sorted ascending, `CUME_DIST` gives the share of rows at or below each (Isha 0.111, ..., Asha 1.000).

### Gaps and islands: find runs of consecutive days

Subtracting a row number from a consecutive sequence gives a **constant** within each run; group on it.

```sql
-- MySQL
SELECT MIN(day) AS first_day, MAX(day) AS last_day, COUNT(*) AS days_in_row FROM (
  SELECT day, DATE_SUB(day, INTERVAL ROW_NUMBER() OVER (ORDER BY day) DAY) AS grp FROM visits
) t GROUP BY grp ORDER BY first_day;

-- PostgreSQL: a date minus an integer is a date
--   SELECT day, day - CAST(ROW_NUMBER() OVER (ORDER BY day) AS INT) AS grp FROM visits
```

| first_day | last_day | days_in_row |
|---|---|---|
| 2026-03-01 | 2026-03-03 | 3 |
| 2026-03-06 | 2026-03-07 | 2 |
| 2026-03-10 | 2026-03-10 | 1 |

Uses: login streaks, uptime periods, consecutive sales days.

### Performance note

A window function sorts or partitions the data; an index on `(partition columns, order columns)` can avoid the sort. For huge tables restrict rows with `WHERE` **before** the window, not after.

---

## 9. Views

A **view** is a saved `SELECT` with a name. It stores **no data**; it is re-run when you query it.

```sql
CREATE VIEW dept_payroll AS
  SELECT d.id AS dept_id, d.name AS dept, COUNT(e.id) AS headcount, COALESCE(SUM(e.salary), 0) AS payroll
  FROM dept d LEFT JOIN emp e ON e.dept_id = d.id GROUP BY d.id, d.name;

SELECT * FROM dept_payroll ORDER BY dept_id;
```

| dept_id | dept | headcount | payroll |
|---|---|---|---|
| 1 | Engineering | 4 | 480000 |
| 2 | Sales | 3 | 270000 |
| 3 | HR | 1 | 70000 |
| 4 | Legal | 0 | 0 |

**Why:** hide complexity, give one definition of a business rule, expose **only some columns or rows** to a user (a security layer: grant access to the view, not the table), keep an old interface alive after a table changes.

### Updatable views and `WITH CHECK OPTION`

A simple view (one table, no aggregates) can be inserted into and updated. `WITH CHECK OPTION` refuses changes that would make a row **disappear from the view**:

```sql
CREATE VIEW sales_staff AS SELECT id, name, dept_id, salary, hired FROM emp WHERE dept_id = 2 WITH CHECK OPTION;

INSERT INTO sales_staff VALUES (10, 'Jay', 2, 60000, '2026-01-05');   -- OK: 4 rows in the view now
INSERT INTO sales_staff VALUES (11, 'Kim', 1, 60000, '2026-01-05');
-- ERROR 1369: CHECK OPTION failed 'adv.sales_staff'     (Kim belongs to dept 1, not the view's dept 2)
```

### Materialized views (PostgreSQL)

A **materialized view** stores the result and is refreshed on demand: fast to read, possibly **stale**.

```sql
CREATE MATERIALIZED VIEW dept_payroll_mv AS SELECT * FROM dept_payroll;
UPDATE emp SET salary = salary + 1000 WHERE id = 8;
-- live view: 71000   materialized view: 70000 (stale)
REFRESH MATERIALIZED VIEW dept_payroll_mv;      -- now 71000
```

MySQL has no materialized views: use a summary table refreshed by a scheduled event or job. Use them for expensive reports that can be a few minutes old.

**Cautions:** a view on a view on a view is hard to tune; a view does not make a slow query fast; `SELECT *` in a view definition is frozen at creation in most databases.

---

## 10. Stored Procedures and Functions

A **stored procedure** or **function** is code saved in the database and run by name. Syntax is **not portable**: MySQL uses its own procedural language, PostgreSQL uses **PL/pgSQL**, Oracle uses PL/SQL.

### MySQL

```sql
DELIMITER //                                         -- so ";" inside the body does not end the statement
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
DELIMITER ;

CALL raise_salary(3, 10, @changed);                  -- @changed is now 1
SELECT id, name, salary, grade_of(salary) AS grade FROM emp WHERE dept_id = 3;     -- Hari | 77000 | junior
CALL raise_salary(3, 80, @changed);                  -- ERROR 1644 (45000): percent must be between 0 and 50
```

### PostgreSQL

```sql
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

SELECT raise_salary(3, 10);                          -- 1
```

(PostgreSQL 11+ also has `CREATE PROCEDURE` and `CALL` when you need transaction control inside.)

| Concept | Procedure | Function |
|---|---|---|
| Called with | `CALL name(...)` | inside a query: `SELECT grade_of(salary)` |
| Returns | nothing, or `OUT` parameters | a value (or a table) |
| Use for | a unit of work (update several tables) | a calculation reused in queries |

### Calling from Java

```java
try (CallableStatement cs = con.prepareCall("{call raise_salary(?, ?, ?)}")) {
    cs.setInt(1, 3);
    cs.setBigDecimal(2, new BigDecimal("10"));
    cs.registerOutParameter(3, Types.INTEGER);
    cs.execute();
    int changed = cs.getInt(3);
}
```

### Pros and cons

| Pros | Cons |
|---|---|
| fewer network round trips for multi-step work | **business logic hidden in the database**: harder to version, review and test |
| runs next to the data (fast for bulk updates) | not portable between database vendors |
| permissions: grant `EXECUTE` without table access | harder to debug; no good IDE support |
| one place for a rule used by many applications | scales the database server (the hardest tier to scale) |

Modern Spring applications keep business rules in Java services and use procedures for **batch jobs and data migrations**. Know both, and be able to argue either way.

---

## 11. Triggers

A **trigger** runs automatically **before or after** `INSERT`, `UPDATE` or `DELETE` on a table, for each row (or each statement in PostgreSQL).

### An audit trigger and a validation trigger (MySQL)

```sql
CREATE TABLE salary_audit (
  id INT AUTO_INCREMENT PRIMARY KEY, emp_id INT NOT NULL,
  old_salary INT NOT NULL, new_salary INT NOT NULL, changed_by VARCHAR(64) NOT NULL);

DELIMITER //
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
```

`OLD` is the row before the change and `NEW` the row after (`NEW` is available for inserts and updates, `OLD` for updates and deletes).

Run inside a transaction (verified in MySQL):

```sql
START TRANSACTION;
CALL raise_salary(3, 10, @changed);                          -- Hari: 70000 -> 77000
SELECT emp_id, old_salary, new_salary FROM salary_audit;     -- 8 | 70000 | 77000   (the trigger wrote it)
UPDATE emp SET salary = -1 WHERE id = 8;                     -- ERROR 1644: salary cannot be negative
ROLLBACK;
SELECT COUNT(*) FROM salary_audit;                           -- 0 : the audit row was rolled back with the change
```

**A trigger runs inside the same transaction** as the statement that fired it, so an error undoes everything and the audit trail never disagrees with the data.

### PostgreSQL version

A trigger needs a **trigger function** plus a `CREATE TRIGGER`:

```sql
CREATE FUNCTION emp_salary_audit_fn() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  IF NEW.salary <> OLD.salary THEN
    INSERT INTO salary_audit (emp_id, old_salary, new_salary, changed_by) VALUES (OLD.id, OLD.salary, NEW.salary, CURRENT_USER);
  END IF;
  RETURN NEW;
END $$;

CREATE TRIGGER emp_salary_audit AFTER UPDATE ON emp FOR EACH ROW EXECUTE FUNCTION emp_salary_audit_fn();
```

An error inside a PostgreSQL transaction **aborts the whole transaction**: later statements fail with "current transaction is aborted, commands ignored until end of transaction block" until you `ROLLBACK`.

### When to use triggers (and when not)

| Good uses | Problems |
|---|---|
| audit trails and history tables | **invisible**: the application does not show them, so surprising side effects |
| enforcing a rule no matter which application writes | cascading triggers are hard to follow and slow bulk loads |
| keeping a denormalized total in sync | hard to test and to debug |
| | can hide bugs; some ORMs (JPA) do not know a row changed |

Prefer constraints (`NOT NULL`, `CHECK`, foreign keys) first, application code second, and triggers for audit and cross-application rules. Also available: `INSTEAD OF` triggers on views (PostgreSQL), and `BEFORE INSERT` triggers to fill defaults.

---

## 12. Reading `EXPLAIN` and Writing Index-Friendly SQL

`EXPLAIN` shows **how the database will run a query**: which index (if any), how many rows it expects to look at. It is the first tool when a query is slow. (`EXPLAIN ANALYZE` in both MySQL 8 and PostgreSQL actually runs it and reports real times.)

Test table: 20,000 rows with `id` (primary key), `email`, `status`, `created`.

### MySQL: no index, then an index

```sql
EXPLAIN SELECT * FROM big WHERE email = 'user777@example.com';
```

| type | key | rows | Extra |
|---|---|---|---|
| **ALL** | NULL | 19321 | Using where |

`type = ALL` is a **full table scan**: every row is read. After `CREATE INDEX idx_big_email ON big (email);`

| type | key | key_len | ref | rows |
|---|---|---|---|---|
| **ref** | idx_big_email | 242 | const | **1** |

| `type` (best to worst) | Meaning |
|---|---|
| `system`, `const` | at most one row, found by primary/unique key |
| `eq_ref` | one row per row of the previous table (join on a unique key) |
| `ref` | rows matching an index value |
| `range` | an index range (`BETWEEN`, `<`, `LIKE 'abc%'`) |
| `index` | a full scan of an **index** (still every row) |
| `ALL` | a full table scan |

Other columns: `possible_keys` (candidates), `key` (the one chosen), `rows` (estimate), `filtered`, and `Extra`: **`Using index`** = a *covering index* (answered from the index alone, without reading the table), `Using filesort` = an extra sort, `Using temporary` = a temporary table.

### What breaks index use (all verified on MySQL)

| Query | Plan | Why |
|---|---|---|
| `WHERE email = 'user777@example.com'` | `ref`, 1 row | direct lookup |
| `WHERE email LIKE 'user77%'` | `range`, 111 rows | a **prefix** match can use the index |
| `WHERE email LIKE '%777@example.com'` | **`ALL`** | a **leading wildcard** cannot use the sort order |
| `WHERE UPPER(email) = 'USER777@EXAMPLE.COM'` | **`ALL`** | a **function on the column** hides it from the index |
| `WHERE created >= '2025-01-01'` with index `(status, created)` | **`ALL`** | the index starts with `status`, which is not in the query |
| `SELECT status, created ... WHERE status = 1 AND created >= '2025-01-01'` | `range`, **`Using index`** | covering: both columns are in the index |

**Sargable** conditions (Search ARGument ABLE) compare the **bare column** to a value. Rewrite instead of wrapping the column:

| Slow | Better |
|---|---|
| `WHERE YEAR(created) = 2025` | `WHERE created >= '2025-01-01' AND created < '2026-01-01'` |
| `WHERE salary + 1000 > 50000` | `WHERE salary > 49000` |
| `WHERE UPPER(email) = 'X'` | store lower-case, or create a **functional index** (below) |
| `WHERE col LIKE '%abc'` | full-text search or a trigram index |
| `WHERE a = 1 OR b = 2` | sometimes `UNION` of two indexed queries |
| implicit conversion (`WHERE varchar_col = 123`) | compare with the right type |

### PostgreSQL

```
Seq Scan on big  (cost=0.00..398.00 rows=1 width=31)            -- before the index
Index Scan using idx_big_email on big  (cost=0.29..8.30 rows=1) -- after CREATE INDEX
```

For `WHERE UPPER(email) = ...` PostgreSQL also does a `Seq Scan` until you build an **expression index**, after which it uses a `Bitmap Index Scan on idx_big_upper_email`:

```sql
CREATE INDEX idx_big_upper_email ON big (UPPER(email));
```

(MySQL 8 supports functional indexes too: `CREATE INDEX ... ((UPPER(email)))`.) Read PostgreSQL plans from the **innermost, most indented** line outward; `cost=a..b` is estimated start-up and total cost in arbitrary units.

### Index design rules

- A **composite index** `(a, b, c)` serves queries on `a`, `a and b`, `a and b and c` (the **leftmost prefix**), but not `b` alone. Put equality columns first, the range column last.
- An index speeds reads and **slows writes** and uses disk: index what you filter, join and sort on, not everything.
- Low-selectivity columns (a boolean, a status with three values) rarely help on their own; combine them in a composite index.
- Keep statistics fresh (`ANALYZE`); the optimizer's row estimates drive its choices.
- Paging with `OFFSET 100000` reads and discards 100,000 rows; use **keyset pagination** (`WHERE id > :last ORDER BY id LIMIT 20`).

---

## 13. MySQL and PostgreSQL Differences at a Glance

| Topic | MySQL 8.4 | PostgreSQL 17 |
|---|---|---|
| `FULL OUTER JOIN` | not supported (emulate with `UNION`) | supported |
| Sorting `NULL` | first in `ASC` | **last** in `ASC`; `NULLS FIRST/LAST` |
| String concat | `CONCAT(a, b)` (`\|\|` is `OR` unless `PIPES_AS_CONCAT`) | `a \|\| b`, `CONCAT` |
| Limit / paging | `LIMIT n OFFSET m` | the same, and `FETCH FIRST n ROWS ONLY` |
| Auto IDs | `AUTO_INCREMENT` | `GENERATED ... AS IDENTITY` or `SERIAL` |
| Rollup | `GROUP BY ... WITH ROLLUP` | `GROUP BY ROLLUP (...)`, `CUBE`, `GROUPING SETS` |
| Date arithmetic | `DATE_ADD(d, INTERVAL 1 DAY)` | `d + INTERVAL '1 day'`, `d + 1` |
| Year of a date | `YEAR(d)` | `EXTRACT(YEAR FROM d)` (also works in MySQL) |
| Procedures | `CREATE PROCEDURE`, `DELIMITER` | `CREATE FUNCTION ... LANGUAGE plpgsql`, `$$` quoting |
| Materialized views | no | yes |
| Transactional DDL | no (implicit commit) | **yes** |
| Boolean | `TINYINT(1)` / `BOOLEAN` alias | real `BOOLEAN` |
| `ROUND(double, n)` | works | needs `NUMERIC` (`ROUND(CAST(x AS NUMERIC), 2)`) |
| `5 / 2` | `2.5000` (use `5 DIV 2` for integer division) | **`2`** (integer division; write `5 / 2.0`) |
| Count with a condition | `SUM(CASE WHEN ... THEN 1 ELSE 0 END)` | the same, or `COUNT(*) FILTER (WHERE ...)` |
| Case sensitivity of text compare | usually case-insensitive collation | case-sensitive; use `ILIKE` |
| Error inside a transaction | statement fails, transaction continues | **whole transaction aborts** |
| Default isolation | `REPEATABLE READ` | `READ COMMITTED` |

Write portable SQL where you can (standard joins, `COALESCE`, `CASE`, `CAST`, CTEs and window functions work in both) and isolate vendor-specific pieces.

---

## 14. NoSQL: What, Why and When

**NoSQL** ("not only SQL") databases give up some relational guarantees to gain **flexible structure, horizontal scaling or a model that fits one access pattern**.

| Family | Model | Examples | Good for | Weak at |
|---|---|---|---|---|
| **Key-value** | `key -> value` | **Redis**, DynamoDB | caching, sessions, counters, queues | queries on the value |
| **Document** | JSON-like documents in collections | **MongoDB**, Couchbase | content, catalogs, flexible records | multi-document joins and transactions (improving) |
| **Wide-column** | rows with many sparse columns, partitioned | Cassandra, HBase | huge write volumes, time series | ad-hoc queries |
| **Graph** | nodes and relationships | Neo4j | social networks, recommendations, fraud rings | bulk tabular analytics |
| **Search** | inverted indexes | Elasticsearch, OpenSearch | full-text search, logs | being a system of record |

### How the thinking differs

| | Relational | Document (MongoDB) |
|---|---|---|
| Structure | fixed schema, tables, rows | flexible documents, nested arrays |
| Relationships | joins across tables | **embed** related data, or store references |
| Design starts from | the data (normalize) | the **queries** (shape the document for the way it is read) |
| Consistency | ACID transactions | per-document atomic; multi-document transactions exist but cost more |
| Scaling | vertical first, then replicas and sharding | built for sharding |

A MongoDB order document (an order *embeds* its lines, so one read returns the whole order; a relational design would use two tables and a join):

```json
{
  "_id": "ord-1001",
  "customer": { "id": 42, "name": "Asha" },
  "lines": [ { "sku": "A1", "qty": 2, "price": 500 }, { "sku": "B7", "qty": 1, "price": 1200 } ],
  "placed": "2026-03-02T10:15:00Z",
  "status": "PAID"
}
```

```js
db.orders.find({ status: "PAID", "customer.id": 42 }).sort({ placed: -1 }).limit(10)
db.orders.aggregate([ { $match: { status: "PAID" } }, { $group: { _id: "$customer.id", total: { $sum: "$lines.price" } } } ])
db.orders.createIndex({ "customer.id": 1, placed: -1 })
```

*(MongoDB commands above were not run for these notes.)*

### Redis in practice

Redis keeps data **in memory** (optionally persisted) and answers in microseconds.

```
SET session:abc123 "{userId:42}" EX 1800        # a session that expires in 30 minutes
INCR page:views:home                            # atomic counter
HSET user:42 name Asha plan gold                # a hash
ZADD leaderboard 1500 asha 1200 ben             # a sorted set
ZREVRANGE leaderboard 0 9 WITHSCORES            # the top ten
```

In Spring it is the usual **cache** (`spring.cache.type=redis`), session store and rate-limit counter.

### CAP and consistency

A distributed data store can offer only two of three during a network partition:

- **C**onsistency: every read sees the latest write.
- **A**vailability: every request gets an answer.
- **P**artition tolerance: it keeps working when the network splits (unavoidable in practice).

So the real choice is **CP** (refuse some requests to stay correct) or **AP** (answer, possibly with stale data, and reconcile later: **eventual consistency**). Relational databases on one server are ACID; many NoSQL stores trade to **BASE** (basically available, soft state, eventually consistent).

### Choosing

> **Default to a relational database (PostgreSQL or MySQL).** It handles most applications, enforces integrity, and supports JSON columns for flexible parts. Add a NoSQL store for a **specific** need: Redis for caching and fast counters, a search engine for full-text, a document store when records are truly self-contained and vary in shape, a graph database for relationship-heavy queries.

Do not pick NoSQL "because it scales" before you have a scaling problem, and do not use it to avoid designing a schema: the schema then lives implicitly in every service that reads the data.

---

## 15. Common Mistakes

| Mistake | Why it is wrong | Fix |
|---|---|---|
| `NOT IN (subquery)` on a nullable column | one `NULL` makes it return nothing | `NOT EXISTS` |
| `x = NULL` | always unknown | `x IS NULL` |
| Right-table filter in `WHERE` after `LEFT JOIN` | turns it into an inner join | put it in `ON` |
| Summing across a one-to-many join | rows repeat, totals inflate | aggregate per table first |
| `SELECT *` | extra columns, breaks on schema change, blocks covering indexes | list the columns |
| Missing join condition | accidental cross join | always an `ON` |
| `GROUP BY` without all non-aggregated columns | error, or arbitrary values in lax modes | group by them all |
| `HAVING` used for row filters | slower, confusing | filter in `WHERE` |
| `UNION` when `UNION ALL` is enough | needless de-duplication | `UNION ALL` |
| `ORDER BY` without a tie-breaker, then paging | rows repeat or vanish between pages | add a unique column |
| `LAST_VALUE` with the default frame | returns the current row | explicit `ROWS BETWEEN ... UNBOUNDED FOLLOWING` |
| `RANK` where `ROW_NUMBER` is meant (or the reverse) | ties give several rows with `rn = 1` | choose deliberately, add a tie-break |
| A function on an indexed column in `WHERE` | full scan | compare the bare column / functional index |
| `LIKE '%text'` on a big table | full scan | full-text search |
| Business rules only in triggers | invisible side effects | constraints and services first |
| Trusting an `EXPLAIN` on a tiny table | the optimizer picks scans for small data | test with realistic volume |
| Integer division in PostgreSQL (`5 / 2` is `2`; MySQL gives `2.5`) | different answers per database | `5 / 2.0` or cast |

---

## 16. Output Prediction Puzzles

Use the sample data in section 1.

**Puzzle 1.** `SELECT COUNT(*), COUNT(dept_id) FROM emp;`

**Puzzle 2.** `SELECT d.name FROM dept d WHERE d.id NOT IN (SELECT dept_id FROM emp);` How many rows? And with `WHERE dept_id IS NOT NULL` added inside the subquery?

**Puzzle 3.** `SELECT d.name, COUNT(e.id) FROM dept d LEFT JOIN emp e ON e.dept_id = d.id WHERE e.salary > 100000 GROUP BY d.id, d.name;` Which departments appear?

**Puzzle 4.** `SELECT SUM(e.salary) FROM emp e JOIN orders o ON o.emp_id = e.id WHERE e.dept_id = 2;` versus `SELECT SUM(salary) FROM emp WHERE dept_id = 2;`

**Puzzle 5.** Salaries in Engineering are 150000, 120000, 120000, 90000. What are `ROW_NUMBER`, `RANK` and `DENSE_RANK` for the **last** employee (90000) when ordered by salary descending?

**Puzzle 6.** For Esha's orders (500, 700, 1200, 150, in date order) what are `SUM(amount) OVER (ORDER BY placed)` and `LAG(amount) OVER (ORDER BY placed)` on the **third** row?

**Puzzle 7.** `SELECT LAST_VALUE(amount) OVER (ORDER BY placed) FROM orders WHERE emp_id = 6 ORDER BY placed;` What does it return (orders 300, 450, 800)?

**Puzzle 8.** `SELECT 5 / NULLIF(0, 0) AS a, COALESCE(NULL, NULL, 7) AS b;`

**Puzzle 9.** In MySQL: `SELECT name FROM emp ORDER BY manager_id, id LIMIT 1;` and the same in PostgreSQL.

**Puzzle 10.** A query `WHERE YEAR(created) = 2025` on an indexed `created` column. Will the index be used? How do you fix it?

**Puzzle 11.** A composite index on `(status, created)`. Which of these can use it: `WHERE status = 1`, `WHERE created > '2025-01-01'`, `WHERE status = 1 AND created > '2025-01-01'`?

**Answers**

1. **`9` and `8`.** `COUNT(*)` counts rows; `COUNT(col)` ignores `NULL`.
2. **`0` rows**, then **1 row (`Legal`)**. A single `NULL` in a `NOT IN` list makes every comparison unknown.
3. **Engineering (3) and Sales (1) only.** The `WHERE` removes the `NULL` rows of the left join, so HR and Legal vanish.
4. **`840000`** versus **`270000`**. The join repeats each salary once per order (4, 3 and 2 times).
5. **`4`, `4`, `3`.** Ties at 120000 give the 90000 salary `RANK` 4 (gap) but `DENSE_RANK` 3.
6. **`2400`** and **`700`.** Running total 500 + 700 + 1200; the previous row's amount is 700.
7. **`300`, `450`, `800`.** The default frame ends at the current row, so `LAST_VALUE` is the current row's value, not 800 for all.
8. **`NULL`** and **`7`.** `NULLIF(0, 0)` is `NULL`, and dividing by `NULL` gives `NULL` instead of an error.
9. **MySQL: `Asha`** (a `NULL` manager sorts first); **PostgreSQL: `Ben`** (`NULL` sorts last).
10. **No.** A function on the column prevents the index. Use `created >= '2025-01-01' AND created < '2026-01-01'`.
11. **The first and third.** The index can only be used from its leftmost column; `created` alone cannot use it.

---

## 17. Interview Questions

**1. Difference between `INNER`, `LEFT`, `RIGHT` and `FULL` joins?**
Inner keeps only matches; left keeps all left rows; right keeps all right rows; full keeps all rows from both. Unmatched sides are filled with `NULL`.

**2. Why can `NOT IN` return no rows even when there are non-matching values?**
If the subquery returns a `NULL`, `x NOT IN (..., NULL)` is `UNKNOWN` for every row. Use `NOT EXISTS`.

**3. `WHERE` vs `HAVING`?**
`WHERE` filters rows before grouping; `HAVING` filters groups after aggregation.

**4. `ON` vs `WHERE` in an outer join?**
`ON` defines which rows match; `WHERE` filters the result and can cancel the outer join's `NULL` rows.

**5. `UNION` vs `UNION ALL`?**
`UNION` removes duplicates (extra work); `UNION ALL` keeps everything and is faster.

**6. What is a CTE, and when do you need a recursive one?**
A named subquery for readability; recursive CTEs walk hierarchies (org charts, category trees) and generate series.

**7. What is a window function? How is it different from `GROUP BY`?**
It computes over a set of related rows without collapsing them, so each row keeps its identity and gains a value (rank, running total, group average).

**8. `ROW_NUMBER`, `RANK` and `DENSE_RANK`?**
`ROW_NUMBER` is unique; `RANK` repeats on ties and skips numbers; `DENSE_RANK` repeats on ties without gaps.

**9. How do you get the top N rows per group?**
Rank with `ROW_NUMBER() OVER (PARTITION BY group ORDER BY ...)` in a subquery and filter `rn <= N`.

**10. What does the window frame control, and what is the default trap?**
Which rows around the current row the function sees. With `ORDER BY` the default ends at the current row, so `LAST_VALUE` returns the current value unless you extend the frame to `UNBOUNDED FOLLOWING`.

**11. View vs materialized view?**
A view stores the query and always runs it; a materialized view stores the result and must be refreshed (fast reads, possible staleness). MySQL has no materialized views.

**12. When would you use a stored procedure or trigger, and what are the downsides?**
For batch work, shared rules and audit. Downsides: hidden logic, harder testing and versioning, vendor lock-in, load on the database server.

**13. How do you tell if a query uses an index?**
`EXPLAIN`: `type` `ref`/`range`/`const` and a `key`, versus `ALL` (full scan). PostgreSQL shows `Index Scan` versus `Seq Scan`.

**14. Why might an index on a column not be used?**
A function or calculation on the column, a leading wildcard, a type mismatch, a composite index whose first column is not used, very low selectivity, or a tiny table.

**15. What is a covering index?**
An index that contains every column the query needs, so the table is not read (`Using index` in MySQL).

**16. SQL vs NoSQL: how do you choose?**
Default to relational for integrity and flexibility of queries. Use NoSQL for a specific need: caching or counters (Redis), flexible self-contained documents (MongoDB), full-text search, graphs.

**17. State the CAP theorem.**
During a network partition a distributed store must choose between consistency and availability; partition tolerance is a given.

---

## 18. Cheat Sheet

| Topic | Remember |
|---|---|
| Joins | inner (matches), left (all left), full (MySQL: UNION emulation), cross (all pairs) |
| Anti-join | `NOT EXISTS`; never `NOT IN` on nullable columns |
| `LEFT JOIN` filter | right-table conditions in `ON` |
| Fan-out | aggregate before joining one-to-many |
| `NULL` | `IS NULL`; aggregates skip it; `COALESCE`, `NULLIF`; sort order differs by DB |
| Clause order | FROM, WHERE, GROUP BY, HAVING, SELECT, ORDER BY, LIMIT |
| CTE | `WITH x AS (...)`; recursive needs anchor, `UNION ALL`, stop condition |
| Set ops | `UNION ALL` fast; `INTERSECT`, `EXCEPT` |
| Ranking | `ROW_NUMBER` unique; `RANK` gaps; `DENSE_RANK` no gaps |
| Window | `fn() OVER (PARTITION BY .. ORDER BY .. ROWS ..)`; not allowed in `WHERE` |
| Frame trap | default frame ends at the current row; `RANGE` includes ties |
| Islands | group by `value - ROW_NUMBER()` |
| Views | no data stored; `WITH CHECK OPTION`; materialized views (PostgreSQL) go stale |
| Procedures | `CALL`; MySQL `DELIMITER`, PostgreSQL `$$` |
| Triggers | `BEFORE`/`AFTER`, `OLD`/`NEW`; same transaction; use sparingly |
| `EXPLAIN` | `ALL` = full scan; `ref`/`range` good; `Using index` = covering |
| Sargable | bare column vs value; no functions, no leading `%` |
| Composite index | leftmost prefix rule |
| NoSQL | Redis, MongoDB, Cassandra, Neo4j, Elasticsearch; CAP; default to relational |

---

## 19. Practice Tasks with Solutions

**Task 1.** List employees who earn more than their **manager**.

```sql
SELECT e.name AS employee, e.salary, m.name AS manager, m.salary AS manager_salary
FROM emp e JOIN emp m ON m.id = e.manager_id
WHERE e.salary > m.salary;
-- no rows in the sample data (Asha earns the most); change a salary to test
```

**Task 2.** For each department, list the employees who earn **more than the department average**.

```sql
SELECT name, dept_id, salary FROM (
  SELECT name, dept_id, salary, AVG(salary) OVER (PARTITION BY dept_id) AS avg_salary
  FROM emp WHERE dept_id IS NOT NULL
) t WHERE salary > avg_salary ORDER BY dept_id, salary DESC;
-- Asha (150000) and Esha (110000); Ben and Chitra (120000) equal the Engineering average of 120000, so they are not above it
```

**Task 3.** The **second highest salary** (handle ties and absence).

```sql
SELECT MAX(salary) AS second_highest FROM emp WHERE salary < (SELECT MAX(salary) FROM emp);                    -- 120000

-- or with DENSE_RANK, which generalises to the Nth
SELECT DISTINCT salary FROM (SELECT salary, DENSE_RANK() OVER (ORDER BY salary DESC) AS r FROM emp) t WHERE r = 2;
```

**Task 4.** Employees who have **never placed an order**.

```sql
SELECT e.name FROM emp e WHERE NOT EXISTS (SELECT 1 FROM orders o WHERE o.emp_id = e.id) ORDER BY e.id;
-- Asha, Ben, Chitra, Dev, Hari, Isha
```

**Task 5.** Total orders **per employee and per month**, plus each employee's **share of the month**.

```sql
SELECT emp_id, EXTRACT(MONTH FROM placed) AS month, SUM(amount) AS total,
       ROUND(100.0 * SUM(amount) / SUM(SUM(amount)) OVER (PARTITION BY EXTRACT(MONTH FROM placed)), 1) AS pct_of_month
FROM orders GROUP BY emp_id, EXTRACT(MONTH FROM placed) ORDER BY month, emp_id;
-- a window function can wrap an aggregate: SUM(SUM(amount)) OVER (...)
```

**Task 6.** Build the management chain **upwards** for Dev (Dev, Ben, Asha).

```sql
WITH RECURSIVE up AS (
  SELECT id, name, manager_id FROM emp WHERE name = 'Dev'
  UNION ALL
  SELECT e.id, e.name, e.manager_id FROM emp e JOIN up ON e.id = up.manager_id
)
SELECT name FROM up;
```

**Task 7.** Find the **longest run of consecutive visit days** (use the islands technique).

```sql
SELECT MAX(days_in_row) AS longest FROM (
  SELECT COUNT(*) AS days_in_row FROM (
    SELECT day, DATE_SUB(day, INTERVAL ROW_NUMBER() OVER (ORDER BY day) DAY) AS grp FROM visits   -- MySQL
  ) t GROUP BY grp
) s;
-- 3
```

**Task 8.** An audit requirement: write a trigger so that deleting an employee copies the row into `emp_archive`. Sketch it for your database, then explain two reasons you might prefer a soft-delete column (`active BOOLEAN`) instead.

```sql
-- MySQL (CREATE TABLE ... LIKE copies the columns and indexes but not the foreign keys)
CREATE TABLE emp_archive LIKE emp;
CREATE TRIGGER emp_before_delete BEFORE DELETE ON emp FOR EACH ROW
  INSERT INTO emp_archive (id, name, dept_id, manager_id, salary, hired)
  VALUES (OLD.id, OLD.name, OLD.dept_id, OLD.manager_id, OLD.salary, OLD.hired);   -- "OLD.*" is not allowed: list the columns
-- DELETE FROM emp WHERE id = 20;  ->  the row is now in emp_archive (verified; inside a transaction it is rolled back with the delete)
-- Soft delete: history stays queryable, restoring is one UPDATE, foreign keys from orders keep working,
-- and the application controls it visibly instead of a trigger doing it behind its back.
```

**Task 9.** Make this query use an index, then prove it with `EXPLAIN`.

```sql
SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';      -- ALL (full scan)
CREATE INDEX idx_big_upper_email ON big ((UPPER(email)));           -- MySQL functional index; PostgreSQL: ON big (UPPER(email))
EXPLAIN SELECT * FROM big WHERE UPPER(email) = 'USER777@EXAMPLE.COM';     -- should now show type ref / key idx_big_upper_email
```

**Task 10.** Design question: model a blog (posts, authors, tags, comments) in a relational database and as MongoDB documents. Decide which data you would **embed** and which you would **reference**, and justify it by the queries (list posts by tag, show a post with its comments, show an author's page).

*End of notes.*
