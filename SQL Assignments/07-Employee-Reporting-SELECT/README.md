# Employee Reporting (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create employee_report_db and the departments and employees tables |
| 02_insert_data.sql | Insert 6 departments and 24 employees |
| 03_queries.sql | 13 report queries by department, salary range and joining year |
| 04_negative_tests.sql | 5 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- departments 1 to N employees

Assumptions:

- The brief is about retrieving data, so there are no UPDATE or DELETE scripts.
- The joining year is taken from joining_date using YEAR().
- The salary range example is 50000 to 80000. BETWEEN includes both ends.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every required column. phone is optional.
- UNIQUE stops duplicate department names, emails and phone numbers.
- The foreign key makes sure every employee has a real department.
- The foreign key also blocks deleting a department that has employees.
- CHECK (salary > 0) is the one validation example.

## Query notes

| Topic | Queries | SQL used |
| --- | --- | --- |
| By department | q1 to q4 | JOIN, WHERE =, IN, LEFT JOIN, GROUP BY |
| By salary range | q5 to q7 | BETWEEN, ORDER BY DESC with LIMIT, HAVING |
| By joining year | q8, q9 | YEAR(), GROUP BY YEAR() |
| Combined filters | q10 to q13 | BETWEEN on dates with AND, LIKE, IS NULL, LEFT JOIN IS NULL |

- WHERE filters rows before grouping. HAVING filters groups after grouping.
- q10 filters dates with BETWEEN '2018-01-01' AND '2020-12-31'. q8 uses YEAR() instead. Both work; the date range can use an index.
- q4 uses LEFT JOIN with COUNT(e.emp_id), so Research shows 0 instead of disappearing.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 24 rows |
| q2 | 7 rows, from Ali Raza 128000 down to Zara Hussain 69000 |
| q3 | 10 rows (Finance 5, Sales 5) |
| q4 | IT 7 / avg 90857.14, Finance 5 / 75800.00, Sales 5 / 62800.00, HR 4 / 60750.00, Marketing 3 / 73666.67, Research 0 |
| q5 | 11 rows, from Daniel Brooks 52000 to Mehwish Riaz 79000 |
| q6 | Ali Raza, Fatima Malik, Priya Nair, Emily Chen, Laura Bennett |
| q7 | IT 90857.14, Finance 75800.00 |
| q8 | Zara Hussain, Ahmed Khan, Lucas Silva, Hira Aslam |
| q9 | 2015: 2, 2016: 2, 2017: 1, 2018: 2, 2019: 3, 2020: 2, 2021: 3, 2022: 4, 2023: 2, 2024: 2, 2025: 1 |
| q10 | Ayesha Siddiq, Emily Chen, Mehwish Riaz, Grace Lee, Jason Miller |
| q11 | Emily Chen, Omar Farooq, Jason Miller, Zara Hussain |
| q12 | Amina Yusuf, Hannah Moore, Mehwish Riaz |
| q13 | Research |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'job_title' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'employees_chk_1' is violated |
| t5 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 6 and 24 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE employee_report_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.
- Workbench adds LIMIT 1000 to result grids by default. All results here are smaller than that.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: department details are kept in departments, not repeated in employees.

## Limitations

| Limitation | Fix |
| --- | --- |
| Ties in salary are not handled in the top 5 | Add a second sort column, such as last_name |
| YEAR(joining_date) in WHERE cannot use an index | Use a date range with BETWEEN, as in q10 |
| Salary range and year are typed into the SQL | Use prepared statements or parameters in an application |
| No salary history, so reports show current salary only | Add a salary_history table |
