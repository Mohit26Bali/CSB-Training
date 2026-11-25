# Organization Database (DDL)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create org_db and the tables |
| 02_insert_data.sql | Insert sample data |
| 03_alter_tables.sql | Add salary and joining_date to employees |
| 04_queries.sql | 8 select queries |
| 05_negative_tests.sql | 8 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04, 05.

## Design

- departments 1 to N employees
- departments 1 to N projects
- employees N to M projects, through employee_projects

Assumptions:

- The brief names three tables. A fourth table, employee_projects, is added because employees and projects are many-to-many.
- Every employee belongs to one department.
- Every project is owned by one department. Staff from any department can work on it.
- No department manager column, to avoid a circular reference.
- Salary is an annual amount. Currency is not stored.

## Constraint notes

- Primary keys identify each row. employee_projects uses (emp_id, project_id), so the same assignment cannot be entered twice.
- NOT NULL is on every column that a row cannot do without. phone and end_date are optional.
- UNIQUE stops duplicate department names, project names, emails and phone numbers. MySQL allows many NULL phones in a UNIQUE column.
- Foreign keys make sure a department, employee or project exists before it is referenced.
- Foreign keys block deleting a parent row that still has children. This is MySQL's default, so it is not written.
- CHECK (budget > 0) is the one validation example.
- role defaults to 'Member' when no role is given.

## ALTER notes

- employees already has rows when salary and joining_date are added.
- Adding them as NOT NULL straight away gives the old rows no valid value.
- A DEFAULT would give every employee the same made-up salary.
- So the columns are added as NULL, filled with UPDATE, then changed to NOT NULL.
- The missing_values query must return 0 first. Otherwise MODIFY fails with "Invalid use of NULL value".

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 10 employees with their department |
| q2 | IT 3, Finance 2, HR 2, Marketing 2, Operations 1, Research and Development 0 |
| q3 | IT avg 91666.67, Finance 75500, Operations 64000, HR 63500, Marketing 63500 |
| q4 | Priya Nair, James Carter, Hina Siddiqui, Omar Farooq |
| q5 | 11 rows |
| q6 | Payroll 3, Cloud 3, Network 2, Spring Campaign 2, Recruitment 1, Warehouse 0 |
| q7 | Daniel Brooks, Hina Siddiqui |
| q8 | Cloud Migration, Warehouse Automation Study, Recruitment Portal |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1, t2 | UNIQUE | 1062 Duplicate entry |
| t3 | NOT NULL | 1048 Column cannot be null |
| t4 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t5 | CHECK | 3819 Check constraint is violated |
| t6 | PRIMARY KEY | 1062 Duplicate entry '5-3' for key 'PRIMARY' |
| t7 | Delete blocked by FK | 1451 Cannot delete or update a parent row |
| t8 | NOT NULL from ALTER | 1048 Column 'salary' cannot be null (run after 03) |

Row counts should be 6, 10, 6 and 11 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the four row counts and the four SELECT * results.
4. 03: DESCRIBE before, missing_values = 0, DESCRIBE after.
5. 04: each query result, run one at a time with Ctrl+Enter.
6. 05: each error in the Output panel, run one at a time with Ctrl+Enter.
7. 05: the row counts at the end.

## Running notes

- Workbench stops a script at the first error, so run 05 one statement at a time.
- 01 has no DROP DATABASE. To start again, run DROP DATABASE org_db; manually first.
- Every UPDATE and DELETE filters on the primary key, so Workbench safe update mode allows them.

## Normalization

- 1NF: each column holds one value, and assignments are in their own table.
- 2NF: role depends on the whole key of employee_projects.
- 3NF: no non-key column depends on another non-key column.

## Limitations

| Limitation | Fix |
| --- | --- |
| Old salaries are overwritten | Add a salary_history table |
| Project end date can be before start date | Add CHECK (end_date >= start_date) |
| Salary can be zero or negative | Add CHECK (salary > 0) |
| Email and phone format are not validated | Validate in the application |
| role accepts any text | Use a roles lookup table |
| location is free text | Use a locations table |
| No department manager | Add a nullable manager FK later |
| A department cannot be deleted while it has staff | Reassign staff first, or use ON DELETE SET NULL / CASCADE |
