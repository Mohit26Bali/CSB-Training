# Multi-Table Queries (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create multi_table_db and the departments, employees, projects and employee_projects tables |
| 02_insert_data.sql | Insert 5 departments, 14 employees, 6 projects and 17 assignments |
| 03_queries.sql | 14 join queries |
| 04_negative_tests.sql | 8 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- departments 1 to N employees
- departments 1 to N projects (the owning department)
- employees N to M projects, through employee_projects
- employees 1 to N employees (manager_id points to another employee)

Assumptions:

- The brief names three tables. employee_projects is added because one employee can work on many projects and one project has many employees.
- manager_id is added so a self join can be shown.
- dept_id on employees can be NULL. Noah Kim is a consultant not yet placed in a department, which shows the difference between INNER and LEFT JOIN.
- Employees can work on projects owned by other departments.

## Constraint notes

- Primary keys identify each row. employee_projects uses (emp_id, project_id), so an employee is on a project only once.
- NOT NULL is on every required column. dept_id, manager_id and end_date are optional.
- UNIQUE stops duplicate department names, emails and project names.
- Foreign keys make sure departments, managers, employees and projects exist before they are referenced.
- The manager_id foreign key points to the same table. That is why managers are inserted first.
- Foreign keys block deleting a department or employee that is still referenced.
- CHECK (salary > 0) is the one validation example.
- role defaults to 'Member'.

## Query notes

| Topic | Queries | Join used |
| --- | --- | --- |
| Join types | q1 to q4 | INNER, LEFT, RIGHT, self join |
| Three and four tables | q5 to q8 | Several JOINs, the same table joined twice with different aliases |
| Joins with grouping | q9 to q11 | LEFT JOIN with COUNT / SUM, COUNT(DISTINCT) across two LEFT JOINs |
| Missing matches | q12 to q14 | LEFT JOIN ... WHERE right side IS NULL |

- INNER JOIN keeps only rows that match on both sides (q1: 13 rows, Noah is missing).
- LEFT JOIN keeps every row from the left table (q2: 14 rows, Noah has NULL department).
- RIGHT JOIN keeps every row from the right table (q3: Legal appears with NULL employee).
- A self join uses the same table twice with two aliases (e for the employee, m for the manager).
- q6 and q7 join departments twice: ed for the employee's department and pd for the project's department.
- q11 needs COUNT(DISTINCT). Joining employees and projects to the same department multiplies the rows, so a plain COUNT would be too high.
- SUM over no rows returns NULL, not 0. So Compliance Review in q9 and Noah Kim in q10 show NULL weekly_hours.
- MySQL has no FULL OUTER JOIN. The usual workaround is a LEFT JOIN UNION a RIGHT JOIN, which is not needed here.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 13 rows (Noah Kim missing) |
| q2 | 14 rows, Noah Kim with NULL department (sorted first) |
| q3 | 14 rows: 13 employees plus Legal with NULL employee |
| q4 | 14 rows. Sara, Fatima, Ali, Laura and Noah have no manager. Zara Hussain's manager is Emily Chen |
| q5 | 6 projects |
| q6 | 17 rows |
| q7 | Emily Chen (IT) on Payroll System Upgrade (Finance); Grace Lee (Finance) on Customer Analytics Dashboard (IT); Hannah Moore (IT) on Recruitment Portal (HR) |
| q8 | Cloud Migration (Ali Raza), Recruitment Portal (Sara Ahmed), Customer Analytics Dashboard (Omar Farooq) |
| q9 | Cloud Migration 4 / 85, Payroll 4 / 50, Brand Refresh 3 / 70, Analytics Dashboard 3 / 45, Recruitment Portal 3 / 38, Compliance Review 0 / NULL |
| q10 | Emily, Omar, Grace and Hannah 2 projects each; Noah Kim 0 |
| q11 | Finance 3 / 1, Human Resources 2 / 1, Information Technology 5 / 2, Legal 0 / 1, Marketing 3 / 1 |
| q12 | Noah Kim |
| q13 | Compliance Review |
| q14 | Legal |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'project_name' cannot be null |
| t3 | FOREIGN KEY (department) | 1452 Cannot add or update a child row |
| t4 | FOREIGN KEY (manager, self) | 1452 Cannot add or update a child row |
| t5 | CHECK | 3819 Check constraint 'employees_chk_1' is violated |
| t6 | PRIMARY KEY | 1062 Duplicate entry '8-3' for key 'PRIMARY' |
| t7 | Delete blocked by FK (department) | 1451 Cannot delete or update a parent row |
| t8 | Delete blocked by FK (manager) | 1451 Cannot delete or update a parent row |

Row counts should be 5, 14, 6 and 17 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter. q1, q2 and q3 side by side show the join types clearly.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE multi_table_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.

## Normalization

- 1NF: each column holds one value. Assignments are in their own table.
- 2NF: role and hours_per_week depend on the whole key of employee_projects.
- 3NF: department details are stored once. The manager is stored as an id, not as a copied name.

## Limitations

| Limitation | Fix |
| --- | --- |
| role accepts any text | Use an ENUM or a roles lookup table |
| Nothing stops an employee being their own manager | Add CHECK (manager_id <> emp_id) |
| hours_per_week is not checked | Add a CHECK such as hours_per_week BETWEEN 1 AND 40 |
| No assignment start or end dates | Add start_date and end_date to employee_projects |
| SUM shows NULL for no rows | Use COALESCE(SUM(...), 0) if 0 is preferred |
