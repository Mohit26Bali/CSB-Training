# Academic Reports (SELECT)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create academic_db and the departments, students, courses and enrollments tables |
| 02_insert_data.sql | Insert 4 departments, 15 students, 8 courses and 38 enrollments |
| 03_queries.sql | 14 queries: student performance and course enrollment statistics |
| 04_negative_tests.sql | 6 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04.

## Design

- departments 1 to N students
- departments 1 to N courses
- students N to M courses, through enrollments

Assumptions:

- Marks are numbers from 0 to 100, so they can be averaged.
- The pass mark is 50.
- Letter grades: A 85+, B 70-84, C 60-69, D 50-59, F below 50.
- marks is NULL while a course is still in progress (Fall 2025).
- A student takes a course only once, so the key is (student_id, course_id).
- Average marks are a simple average, not weighted by credits.

## Constraint notes

- Primary keys identify each row. enrollments uses (student_id, course_id), so a student cannot be enrolled in the same course twice.
- NOT NULL is on every column except marks.
- UNIQUE stops duplicate department names, roll numbers, student emails and course codes.
- Foreign keys make sure every student and course has a real department, and every enrollment has a real student and course.
- Foreign keys also block deleting a student who has enrollments.
- CHECK (marks BETWEEN 0 AND 100) is the one validation example. A NULL mark passes the CHECK, which is why in-progress rows are allowed.

## Query notes

| Topic | Queries | Technique |
| --- | --- | --- |
| Student performance | q1 to q7 | CASE for letter grades, AVG / MIN / MAX per student, LIMIT, HAVING AVG, GROUP BY department and intake year |
| Course statistics | q8 to q14 | LEFT JOIN with COUNT, percentage of capacity, SUM(CASE) for pass/fail counts, IS NULL, LEFT JOIN IS NULL |

- Every marks query uses WHERE e.marks IS NOT NULL. AVG ignores NULLs anyway, but the filter makes this clear and keeps in-progress rows out of the counts.
- COUNT(e.marks) counts only graded rows. COUNT(*) counts all rows. q11 shows both.
- q10 uses SUM(CASE WHEN marks >= 50 THEN 1 ELSE 0 END). Each passing row adds 1, so the sum is the number of passes.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 34 graded rows: A 9, B 12, C 5, D 3, F 5 |
| q2 | 14 students, from Zainab Hussain 92.33 down to Noah Kim 42.50 |
| q3 | Zainab Hussain 92.33, Hira Aslam 90.00, Daniel Park 89.50 |
| q4 | Noah Kim MTH101 38, Lucas Gray MTH202 44, Ethan Walker MTH101 45, Noah Kim CS101 47, Ryan Hughes BUS101 49 |
| q5 | Noah Kim 42.50, Ethan Walker 51.67, Lucas Gray 52.50, Ryan Hughes 55.00 |
| q6 | Mathematics 75.29, Computer Science 71.06, Business 70.80 |
| q7 | 2023 intake 79.58, 2024 intake 62.07 |
| q8 | MTH101 10 (20.0%), CS101 7, BUS101 5, BUS205 5, CS201 5, CS301 3, MTH202 3, PHY101 0 |
| q9 | CS201 75.60, CS101 74.43, BUS101 72.40, MTH101 69.70, MTH202 69.67, BUS205 69.00 |
| q10 | MTH101 8 passed / 2 failed, BUS101 4 / 1, CS101 6 / 1, MTH202 2 / 1, BUS205 4 / 0, CS201 5 / 0 |
| q11 | Fall 2024 21 / 21 graded, Spring 2025 13 / 13, Fall 2025 4 / 0 |
| q12 | Hamza, Olivia and Zainab in CS301; Hira Aslam in BUS205 |
| q13 | PHY101 Physics I |
| q14 | Amna Javed |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1 | UNIQUE | 1062 Duplicate entry |
| t2 | NOT NULL | 1048 Column 'course_name' cannot be null |
| t3 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t4 | CHECK | 3819 Check constraint 'enrollments_chk_1' is violated |
| t5 | PRIMARY KEY | 1062 Duplicate entry '1-1' for key 'PRIMARY' |
| t6 | Delete blocked by FK | 1451 Cannot delete or update a parent row |

Row counts should be 15 students and 38 enrollments after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: each query result, run one at a time with Ctrl+Enter.
5. 04: each error in the Output panel, run one at a time with Ctrl+Enter.
6. 04: the row counts at the end.

## Running notes

- To start again, run DROP DATABASE academic_db; and then 01 to 04.
- Workbench stops a script at the first error, so run 04 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: in enrollments, semester and marks depend on the whole key (this student in this course).
- 3NF: department, student and course details are stored once and referenced by key.

## Limitations

| Limitation | Fix |
| --- | --- |
| A student cannot retake a course | Add semester to the enrollments primary key |
| Semester is text, so it sorts alphabetically, not by date | Use a semesters table with a start date |
| Average is not weighted by credits | Use SUM(marks * credits) / SUM(credits) |
| Grade boundaries are written in the query | Store them in a grade_scale table |
| Capacity is not enforced | Check the count before enrolling, in the application or with a trigger |
