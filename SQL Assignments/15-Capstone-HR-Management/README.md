# Capstone: HR Management Database

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Part | Purpose |
| --- | --- | --- |
| 01_create_tables.sql | DDL | Create hr_management_db and 5 tables |
| 02_insert_data.sql | DML | Insert 6 departments, 11 jobs, 25 employees, 5 salary history rows and 16 leave requests |
| 03_alter_tables.sql | DDL | Add termination_date, widen phone |
| 04_hr_operations.sql | DML | New hire, raises, promotion, pay correction, leave approvals, termination, cleanup |
| 05_management_reports.sql | SELECT | 14 management reports |
| 06_negative_tests.sql | Tests | 9 statements that should fail |
| screenshots/ | | Captured outputs |

Run order: 01, 02, 03, 04, 05, 06.

## Design

| Table | Purpose | Key relationships |
| --- | --- | --- |
| departments | Business units with an annual budget | 1 to N employees |
| jobs | Job titles with a salary range | 1 to N employees |
| employees | Staff | N to 1 department, N to 1 job, N to 1 manager (self) |
| salary_history | Every salary change | N to 1 employee |
| leave_requests | Leave applications | N to 1 employee |

Assumptions:

- "Today" is fixed as 2025-10-06, so results are the same on every run.
- "Current" employees are Active or On Leave. Terminated employees are kept for history and left out of headcount and pay reports.
- HR records are never deleted for people with history. Leavers are marked Terminated instead.
- Leave days count both the start and end date (DATEDIFF + 1), including weekends.
- Old rejected leave requests (ended before 2025-07-01) are treated as obsolete and deleted.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every required column. phone, manager_id and termination_date are optional.
- UNIQUE stops duplicate department names, job titles, emails and phone numbers.
- Foreign keys link employees to departments, jobs and managers, and link history and leave to employees.
- Foreign keys also block deleting a department, job or employee that is still referenced.
- CHECK (salary > 0) is the one validation example.
- status, leave_type and leave status are ENUMs with sensible defaults ('Active', 'Pending').
- Managers are inserted first in 02, because manager_id must point to an existing employee.

## DDL notes (03)

- termination_date is added as a nullable column. Existing employees have not left, so NULL is correct for them and no backfill is needed.
- MODIFY COLUMN phone VARCHAR(20) keeps the UNIQUE index. Indexes are separate from the column definition, so writing UNIQUE again would create a second index. SHOW INDEX proves the original is still there.

## DML notes (04)

- Every pay change writes a salary_history row and updates the salary in one transaction, so the two never disagree.
- INSERT ... SELECT copies the current salary into salary_history before the UPDATE changes it.
- The Engineering raise must log first and update second. In the other order, the history would record the new salary as the old one.
- Safe update mode is turned off at the start and back on at the end, because some WHERE clauses do not use a key.
- Run 04 only once. Running it again would give a second raise and fail on the duplicate new hire.

## Report notes (05)

| Topic | Reports | Technique |
| --- | --- | --- |
| Workforce | r1 to r5 | LEFT JOIN with filter in ON, self join, YEAR(), LEFT JOIN IS NULL |
| Pay | r6 to r11 | SUM / AVG / MIN / MAX, budget percentage, CASE bands, NOT BETWEEN, derived table of department averages |
| Leave | r12 to r14 | SUM(DATEDIFF + 1), WHERE on status, LEFT JOIN with filter in ON |

- r10 joins each employee to a derived table of department averages. This is a non-correlated nested query: it runs once.
- r14 puts l.status = 'Approved' in the ON clause. In WHERE it would remove employees who have no leave rows at all.

## Expected results

After 04:

- 26 employees, 14 salary history rows, 14 leave requests (rejected requests 7 and 8 deleted).
- Engineering salaries: Ali 152250, Emily 115500, Omar 96600, Hannah 88200, Lucas 75600, Zara 67200.
- Kevin Wright: 76000 to 79800 (raise), then 88000 (promotion to Software Engineer).
- Bilal Ahmed: 52000 to 55000. Tom Reed: Terminated on 2025-10-06. Nadia Rahman: new HR Officer.

| Report | Expected |
| --- | --- |
| r1 | Engineering 7, Sales 6, Finance 4, Human Resources 4, Operations 4, Legal 0 |
| r2 | 25 rows. Hannah Moore and Lucas Silva report to Emily Chen |
| r3 | 2014: 1, 2015: 2, 2016: 2, 2017: 2, 2018: 3, 2019: 2, 2020: 3, 2021: 2, 2022: 1, 2023: 3, 2024: 3, 2025: 2 |
| r4 | Tom Reed, Operations, left 2025-10-06 |
| r5 | Legal |
| r6 | Engineering 683350.00 (avg 97621.43), Sales 390000.00 (65000.00), Finance 324000.00 (81000.00), Operations 260000.00 (65000.00), HR 253000.00 (63250.00) |
| r7 | Engineering 56.9%, HR 56.2%, Sales 55.7%, Finance 54.0%, Operations 52.0% |
| r8 | Under 50,000: 4, 50,000-74,999: 10, 75,000-99,999: 7, 100,000+: 4 |
| r9 | Adam Fisher, 74000, above the Sales Executive maximum of 70000 |
| r10 | Ali Raza, Emily Chen, Fatima Malik, Sara Ahmed, Hina Siddiqui, Priya Nair, Adam Fisher |
| r11 | 14 rows, from James Carter 2024-04-01 to Kevin Wright's promotion on 2025-10-06 |
| r12 | Grace Lee 91 days, Emily Chen 12, then five people with 5 and four with 2 |
| r13 | Kevin Wright, Annual, 2025-11-03 to 2025-11-07 |
| r14 | 15 current employees with no approved leave |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'last_name' cannot be null |
| t3 | FOREIGN KEY (job) | 1452 Cannot add or update a child row |
| t4 | FOREIGN KEY (manager, self) | 1452 Cannot add or update a child row |
| t5 | CHECK | 3819 Check constraint 'employees_chk_1' is violated |
| t6 | ENUM | 1265 Data truncated for column 'status' |
| t7 | Delete blocked (leaver with history) | 1451 Cannot delete or update a parent row |
| t8 | Delete blocked (department) | 1451 Cannot delete or update a parent row |
| t9 | Delete blocked (job in use) | 1451 Cannot delete or update a parent row |

Row counts should be 6 departments, 11 jobs and 26 employees after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: DESCRIBE before and after, and SHOW INDEX.
5. 04: the Output panel row counts for each statement, the three check queries, and the row counts.
6. 05: each report, run one at a time with Ctrl+Enter.
7. 06: each error in the Output panel, run one at a time with Ctrl+Enter.
8. 06: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE hr_management_db; and then 01 to 06.
- Workbench stops a script at the first error, so run 06 one statement at a time.
- The full pipeline (01 to 05) was checked by running it on SQLite. Run it in MySQL yourself and compare with the tables above.

## Normalization

- 1NF: each column holds one value. Salary changes and leave are in their own tables, not repeated columns.
- 2NF: every table has a single-column key.
- 3NF: department, job and manager details are stored once and referenced by id. Salary ranges live in jobs, not on each employee.

## Limitations

| Limitation | Fix |
| --- | --- |
| Salary within the job range is reported (r9) but not enforced | Check in the application or with a trigger |
| Leave end date is not checked against start date | Add CHECK (end_date >= start_date) |
| Leave days include weekends and holidays | Add a calendar table of working days |
| Overlapping leave requests are allowed | Check for overlaps before approving |
| Nothing stops an employee managing themselves | Add CHECK (manager_id <> emp_id) |
| "Today" is a fixed date | Use CURDATE() in a live system |
| No user or audit trail on changes | Add changed_by and changed_at columns |
