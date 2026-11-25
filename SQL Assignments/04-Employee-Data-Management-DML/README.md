# Employee Data Management (DML)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create employee_dml_db and the departments, employees and assets tables |
| 02_insert_data.sql | Insert 6 departments, 50 employees and 10 assets |
| 03_update_salaries.sql | Salary raises by department rules |
| 04_delete_inactive.sql | Delete inactive employees without breaking constraints |
| 05_queries.sql | 8 select queries |
| 06_negative_tests.sql | 7 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04, 05, 06.

## Design

- departments 1 to N employees
- employees 1 to N assets (an asset is held by at most one employee)

Assumptions:

- "Preserving business constraints" means an inactive employee who still holds company assets (laptop, phone, access card) must not be deleted. The assets table holds this rule through a foreign key.
- Department rules are percentage raises for active employees only: HR 5%, Finance 6%, IT 10%, Sales 8%, Operations 4%.
- After the raises, any active employee below 45000 is raised to 45000.
- Inactive employees get no raise.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every required column. assets.emp_id is optional (an asset can be in storage).
- UNIQUE stops duplicate department names, employee emails and asset tags.
- Foreign keys make sure a department exists for every employee and an employee exists for every assigned asset.
- The foreign key on assets blocks deleting an employee who still holds an asset.
- CHECK (salary > 0) is the one validation example.
- status is an ENUM, so only 'Active' or 'Inactive' is accepted. It defaults to 'Active'.

## DML notes

- Workbench safe update mode blocks UPDATE and DELETE when the WHERE does not use a key column. Scripts 03 and 04 turn it off at the start and back on at the end.
- Each department rule is its own UPDATE, so each one is easy to explain and check.
- The 45000 minimum runs after the raises, so it checks the new salaries.
- The delete removes only inactive employees whose emp_id is not in assets.
- The subquery needs WHERE emp_id IS NOT NULL. One asset has no holder, and NOT IN with a NULL in the list matches nothing, so the delete would remove no rows.
- A plain DELETE of all inactive employees would fail with error 1451 and remove nothing. Test t6 shows this for one employee.

## Expected results

Before and after 03 (active employees only):

| Department | Before total | Before avg | After total | After avg |
| --- | --- | --- | --- | --- |
| Human Resources | 545000.00 | 60555.56 | 572250.00 | 63583.33 |
| Finance | 649000.00 | 72111.11 | 687940.00 | 76437.78 |
| Information Technology | 736000.00 | 92000.00 | 809600.00 | 101200.00 |
| Sales | 536000.00 | 67000.00 | 578880.00 | 72360.00 |
| Operations | 512000.00 | 64000.00 | 534840.00 | 66855.00 |

- Rule 6 changes only Ben Carter (42640.00 to 45000.00).
- The 8 inactive salaries do not change.

04:

- Before the delete there are 8 inactive employees. Kevin Wright, Faisal Mehmood and George Hall hold assets.
- 5 rows are deleted: Hassan Ali, Kamran Javed, Usman Tariq, Saima Bano and Asif Iqbal.
- After the delete there are 45 employees: 42 active and 3 inactive.

| Query | Expected |
| --- | --- |
| q1 | 45 rows |
| q2 | 9 in each of the five departments, Legal 0 |
| q3 | IT 101200.00, Finance 76437.78, Sales 72360.00, Operations 66855.00, HR 63583.33 |
| q4 | 10 rows, from Ali Raza 137500.00 down to Jason Miller 92400.00 |
| q5 | Active 42, Inactive 3 |
| q6 | 10 assets. LT-1006 has no holder |
| q7 | Kevin Wright, Faisal Mehmood, George Hall |
| q8 | Legal |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'last_name' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'employees_chk_1' is violated |
| t5 | ENUM | 1265 Data truncated for column 'status' |
| t6 | Delete blocked by FK | 1451 Cannot delete or update a parent row |
| t7 | FOREIGN KEY on update | 1452 Cannot add or update a child row |

Row counts should be 6, 45 and 10 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts, the status counts (42 / 8) and the employees table.
4. 03: the before and after salary summaries, plus the inactive salaries.
5. 04: the inactive list with assets_held, the Output panel line showing "5 row(s) affected", the kept inactive employees, and the counts after.
6. 05: each query result, run one at a time with Ctrl+Enter.
7. 06: each error in the Output panel, run one at a time with Ctrl+Enter.
8. 06: the row counts at the end.

## Running notes

- Run 03 and 04 only once. Running 03 again would give a second raise.
- To start again, run DROP DATABASE employee_dml_db; and then 01 to 06.
- Workbench stops a script at the first error, so run 06 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: department details are kept in departments, not repeated in employees.

## Limitations

| Limitation | Fix |
| --- | --- |
| Deleted employees are gone, with no history kept | Keep them as Inactive, or copy them to an archive table first |
| Old salaries are overwritten | Add a salary_history table |
| Running 03 twice gives a double raise | Record each raise in a log table and check it first |
| Updates and deletes are not in a transaction | Use START TRANSACTION and COMMIT, so a mistake can be rolled back |
| Department raise percentages are written in the SQL | Store them in a table and update with a join |
