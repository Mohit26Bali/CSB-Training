# University Management Schema (DDL)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create university_db and the students, courses and enrollments tables |
| 02_insert_data.sql | Insert sample data |
| 03_alter_tables.sql | Add the instructors table, plus duration and instructor columns on courses |
| 04_queries.sql | 8 select queries |
| 05_negative_tests.sql | 9 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04, 05.

## Design

- students N to M courses, through enrollments
- instructors 1 to N courses (added in 03)

Assumptions:

- Instructor information is stored in its own instructors table, and courses get an instructor_id foreign key. This avoids repeating instructor details on every course.
- Each course has one instructor. instructor_id can be NULL while a course is unassigned.
- Course duration is stored in weeks.
- A student can enroll in a course only once, so the key is (student_id, course_id).
- grade is NULL while a course is still in progress.

## Constraint notes

- Primary keys identify each row. enrollments uses (student_id, course_id), so the same enrollment cannot be entered twice.
- NOT NULL is on every required column. phone, grade and instructor_id are optional.
- UNIQUE stops duplicate student emails, phone numbers, course codes and instructor emails.
- Foreign keys make sure a student, course or instructor exists before it is referenced.
- Foreign keys also block deleting a student or course that still has enrollments. This is MySQL's default.
- CHECK (credits > 0) is the one validation example.

## ALTER notes

- The brief asks to modify the schema, so instructors and the new columns are added in 03, not in 01.
- courses already has rows, so duration_weeks is added as NULL, filled with UPDATE, then changed to NOT NULL.
- The missing_durations query must return 0 first. Otherwise MODIFY fails with "Invalid use of NULL value".
- instructor_id stays nullable, so a course can exist before an instructor is assigned.
- The foreign key on instructor_id is added in the same ALTER as the column.

## Expected results

| Query | Expected |
| --- | --- |
| q1 | 15 rows |
| q2 | CS101 4, DB301 3, CS201 2, EN102 2, MA101 2, PH110 2, CS450 0 |
| q3 | Hamza 10, Ethan 8, Noah 8, Zainab 7, Ayesha 6, Mariam 5, Olivia 5 |
| q4 | 6 courses (CS450 has no instructor, so it is not shown) |
| q5 | Robert Hayes 2 courses (32 weeks), the other four instructors 1 course each |
| q6 | Olivia EN102, Zainab DB301, Noah CS201, Ayesha CS101 |
| q7 | Leo Martins |
| q8 | CS450 Machine Learning |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1, t2 | UNIQUE | 1062 Duplicate entry |
| t3 | NOT NULL | 1048 Column 'course_name' cannot be null |
| t4 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t5 | CHECK | 3819 Check constraint is violated |
| t6 | PRIMARY KEY | 1062 Duplicate entry '1-1' for key 'PRIMARY' |
| t7 | Delete blocked by FK | 1451 Cannot delete or update a parent row |
| t8 | NOT NULL from ALTER | 1048 Column 'duration_weeks' cannot be null (run after 03) |
| t9 | FOREIGN KEY from ALTER | 1452 Cannot add or update a child row (run after 03) |

Row counts should be 8, 7, 15 and 5 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: DESCRIBE courses before, missing_durations = 0, then SHOW TABLES, both DESCRIBEs and both SELECT * results after.
5. 04: each query result, run one at a time with Ctrl+Enter.
6. 05: each error in the Output panel, run one at a time with Ctrl+Enter.
7. 05: the row counts at the end.

## Running notes

- Workbench stops a script at the first error, so run 05 one statement at a time.
- 01 has no DROP DATABASE. To start again, run DROP DATABASE university_db; manually first.
- Every UPDATE and DELETE filters on the primary key, so Workbench safe update mode allows them.

## Normalization

- 1NF: each column holds one value, and enrollments are in their own table.
- 2NF: enrollment_date and grade depend on the whole key of enrollments.
- 3NF: instructor details are kept in instructors, not repeated in courses.

## Limitations

| Limitation | Fix |
| --- | --- |
| A course can have only one instructor | Add a course_instructors link table |
| A student cannot retake a course | Add a semester column to the enrollments key |
| grade accepts any two characters | Use a grades lookup table |
| Duration can be zero or negative | Add CHECK (duration_weeks > 0) |
| No semester or term is stored | Add a semesters table |
| instructors.department is free text | Add a departments table with a foreign key |
