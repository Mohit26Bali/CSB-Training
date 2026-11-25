# HR Dashboard Queries (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create hr_dashboard_db and the departments and employees tables |
| 02_insert_data.sql | Insert 6 departments and 30 employees |
| 03_queries.sql | 14 dashboard queries: counts, department summaries and salary distribution |
| 04_negative_tests.sql | 6 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- departments 1 to N employees

Assumptions:

- "Current" employees are those with status Active or On Leave. Resigned employees are kept for history but left out of headcount and salary reports.
- Salaries are annual.
- Salary bands are: under 50,000; 50,000 to 74,999; 75,000 to 99,999; and 100,000 and above.
- Each department has an annual budget, so salary cost can be compared with it.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every column.
- UNIQUE stops duplicate department names and employee emails.
- The foreign key makes sure every employee has a real department.
- The foreign key also blocks deleting a department that has employees.
- CHECK (salary > 0) is the one validation example.
- job_level, employment_type and status are ENUMs. employment_type defaults to 'Full-Time' and status to 'Active'.

## Query notes

| Topic | Queries | Technique |
| --- | --- | --- |
| Employee counts | q1 to q6 | COUNT, GROUP BY status / department / type / level / year |
| Department summaries | q7 to q10 | SUM, AVG, MIN, MAX, budget arithmetic, HAVING, LEFT JOIN IS NULL |
| Salary distribution | q11 to q14 | CASE for salary bands, MIN / AVG / MAX by level, LIMIT, WHERE filter |

- q3 puts e.status <> 'Resigned' in the LEFT JOIN's ON clause, not in WHERE. A WHERE filter would remove Legal, because Legal has no employee rows. With the filter in ON, Legal still shows 0.
- q11 uses CASE to give each salary a band label. The labels start with 1. to 4. so ORDER BY sorts them from lowest band to highest.
- ENUM columns sort in the order they were defined, so q5 and q12 list Junior, Mid, Senior, Manager.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 30 |
| q2 | Active 25, Resigned 3, On Leave 2 |
| q3 | IT 8, Sales 6, Finance 5, Human Resources 4, Operations 4, Legal 0 |
| q4 | Full-Time 23, Part-Time 3, Contract 1 |
| q5 | Junior 7, Mid 9, Senior 6, Manager 5 |
| q6 | 2014: 1, 2015: 2, 2016: 2, 2017: 3, 2018: 3, 2019: 2, 2020: 4, 2021: 3, 2022: 2, 2023: 3, 2024: 4, 2025: 1 |
| q7 | IT 686000.00 (avg 85750.00), Finance 406000.00 (81200.00), Sales 381000.00 (63500.00), Operations 257000.00 (64250.00), HR 241000.00 (60250.00) |
| q8 | IT 68.6%, Finance 67.7%, HR 60.2%, Sales 54.4%, Operations 51.4% |
| q9 | IT 8, Sales 6, Finance 5 |
| q10 | Legal |
| q11 | Under 50,000: 6, 50,000-74,999: 9, 75,000-99,999: 8, 100,000+: 4 |
| q12 | Junior avg 44428.57, Mid 65000.00, Senior 87833.33, Manager 109600.00 |
| q13 | Ali Raza 132000, Fatima Malik 118000, Emily Chen 108000, Priya Nair 105000, Ayesha Siddiq 99000 |
| q14 | Ben Carter, Nathan Price, Tom Reed, Hira Aslam, Bilal Ahmed, Amina Yusuf |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'hire_date' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'employees_chk_1' is violated |
| t5 | ENUM | 1265 Data truncated for column 'job_level' |
| t6 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 6 and 30 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE hr_dashboard_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: department details and budget are kept in departments, not repeated in employees.

## Limitations

| Limitation | Fix |
| --- | --- |
| Salary bands are written in the query | Store bands in a salary_bands table and join on salary BETWEEN min and max |
| No leaving date for resigned staff, so attrition by year is not possible | Add a leave_date column |
| Part-time salaries are not adjusted to full-time equivalent | Add an fte column and report salary / fte |
| No salary history | Add a salary_history table |
| No gender or diversity fields | Add them only if the business needs them and data protection allows it |
