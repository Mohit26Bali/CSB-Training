# Library Management (DML)

Database: MySQL 8.0.16 or later. Tool: MySQL Workbench.

## Files

| File | Purpose |
| --- | --- |
| 01_create_tables.sql | Create library_db and the books, members and issues tables |
| 02_insert_data.sql | Load 15 books, 10 members and 14 issue records |
| 03_update_status.sql | Issue, return, renew, mark lost, withdraw books and expire members |
| 04_remove_obsolete.sql | Delete obsolete books and members safely |
| 05_queries.sql | 8 select queries |
| 06_negative_tests.sql | 8 statements that should fail |
| screenshots/ | Captured outputs |

Run order: 01, 02, 03, 04, 05, 06.

## Design

- books 1 to N issues
- members 1 to N issues
- books N to M members, through issues

Assumptions:

- Each row in books is one physical copy.
- "Today" is fixed as 2025-10-06, so the results are the same every time the scripts run.
- A book is marked Lost if it is still not returned 90 days after its due date.
- Technology books published before 1996 are outdated and are withdrawn.
- Obsolete records are withdrawn books that were never issued, and expired members who never borrowed. Anything with issue history is kept.

## Constraint notes

- Primary keys identify each row.
- NOT NULL is on every required column. phone and return_date are optional (return_date is NULL while a book is out).
- UNIQUE stops duplicate ISBNs, member emails and phone numbers.
- Foreign keys make sure every issue has a real book and member.
- Foreign keys also block deleting a book or member that has issue history.
- CHECK (due_date > issue_date) is the one validation example. It is written at table level because it uses two columns.
- Book status and membership status are ENUMs. Both default to the normal value ('Available', 'Active').

## DML notes

- Issuing and returning a book each change two tables, so each is done inside START TRANSACTION and COMMIT.
- Update 4 uses IN with a subquery to find unreturned books that are more than 90 days overdue.
- Updates 5 and 6 check the current status in the WHERE, so a book or member is only changed once.
- In 04, the NOT IN subqueries are safe because issues.book_id and issues.member_id are NOT NULL.
- Safe update mode is turned off in 03 and 04, and back on at the end.

## Expected results

After 03:

- Database System Concepts: Issued (to Emily Chen, issue 15).
- Clean Code: Available (returned by Daniel Brooks on 2025-10-06).
- Sapiens: due date changes to 2025-10-29.
- The Selfish Gene: Lost (due 2025-05-16, never returned).
- Getting Started with MS-DOS 6 and Mastering Windows 95: Withdrawn.
- James Carter, Omar Farooq and Lucas Silva: Expired.

After 04:

- MS-DOS 6 is deleted (1 row). Mastering Windows 95 is kept, because it has issue history.
- Omar Farooq and Lucas Silva are deleted (2 rows). James Carter is kept, because he has issue history.
- Row counts are 14 books, 8 members and 15 issues.

| Query | Expected |
| --- | --- |
| q1 | 5 rows: Selfish Gene, 1984, Pragmatic Programmer, Database System Concepts, Sapiens |
| q2 | Available 8, Issued 4, Lost 1, Withdrawn 1 |
| q3 | Technology 6, Fiction 4, Science 3, History 2 |
| q4 | Selfish Gene (Bilal, Lost), 1984 (Fatima), Pragmatic Programmer (Ayesha) |
| q5 | Daniel 3, six members with 2, Nadia 0 |
| q6 | Pride and Prejudice (Daniel), Guns, Germs, and Steel (Bilal) |
| q7 | Learning SQL |
| q8 | History 19.0, Fiction 15.7, Science 13.0, Technology 11.0 |

| Test | Constraint | Expected error |
| --- | --- | --- |
| t1, t2 | UNIQUE | 1062 Duplicate entry |
| t3 | NOT NULL | 1048 Column 'title' cannot be null |
| t4 | FOREIGN KEY | 1452 Cannot add or update a child row |
| t5 | CHECK | 3819 Check constraint 'issues_chk_1' is violated |
| t6 | ENUM | 1265 Data truncated for column 'status' |
| t7 | Delete blocked by FK (book history) | 1451 Cannot delete or update a parent row |
| t8 | Delete blocked by FK (member history) | 1451 Cannot delete or update a parent row |

Row counts should be 14, 8 and 15 after the tests.

## Screenshots to take

1. SELECT VERSION(); the result must be 8.0.16 or higher.
2. 01: SHOW TABLES and each DESCRIBE.
3. 02: the row counts and the SELECT * results.
4. 03: book status before and after, the members list, and issues 2, 5 and 15.
5. 04: both preview lists, the "1 row(s) affected" and "2 row(s) affected" lines in the Output panel, and the counts after.
6. 05: each query result, run one at a time with Ctrl+Enter.
7. 06: each error in the Output panel, run one at a time with Ctrl+Enter.
8. 06: the row counts at the end.

## Running notes

- Run 03 and 04 only once. Running 03 again would issue the same book twice.
- To start again, run DROP DATABASE library_db; and then 01 to 06.
- Workbench stops a script at the first error, so run 06 one statement at a time.

## Normalization

- 1NF: each column holds one value.
- 2NF: every table has a single-column key.
- 3NF: book and member details are stored once and referenced by issues.

## Limitations

| Limitation | Fix |
| --- | --- |
| One row per copy, so title details repeat for multiple copies | Split into titles and copies tables |
| Nothing stops issuing a book that is already Issued | Check status in the application, or use a trigger |
| No fines are calculated for late returns | Add a fines table using DATEDIFF on return_date and due_date |
| "Today" is a fixed date | Use CURDATE() in a live system |
| Old issue history is never archived | Move old returned issues to an archive table |
