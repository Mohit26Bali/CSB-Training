# SQL advanced scripts

The scripts behind [`SQL_Advanced_Joins_Windows_Views_and_Procedures_Notes.md`](../../SQL_Advanced_Joins_Windows_Views_and_Procedures_Notes.md). Every query in the note was run from these files on **MySQL 8.4** and **PostgreSQL 17**, and the results in the note are the real results.

| File | What it runs | MySQL | PostgreSQL |
| --- | --- | --- | --- |
| `00-setup.sql` | creates and fills `dept`, `emp`, `orders`, `visits` | yes | yes |
| `01-joins-nulls-grouping-cte.sql` | joins, `NOT IN` trap, `NULL` logic, grouping, recursive CTEs, set operations | yes | yes |
| `02-window-functions.sql` | ranking, running totals, frames, `NTILE`, de-duplication | yes | yes |
| `02b-islands-*.sql` | gaps and islands (the date arithmetic differs) | `-mysql` | `-postgresql` |
| `03a-rollup-*.sql` | `ROLLUP` (the syntax differs) | `-mysql` | `-postgresql` |
| `04-views-procedures-triggers-explain-*.sql` | views, procedures, triggers, `EXPLAIN` | `-mysql` | `-postgresql` |
| `05-exercises.sql` | the practice-task answers | yes | yes |
| `06-more-mysql.sql` | longest run, archive trigger, functional index | yes | |

## Run them

Use a **scratch** database, not one that holds anything you care about. The scripts create and drop tables with the names above.

```bash
# MySQL
mysql -u root -p -e "CREATE DATABASE adv"
mysql -u root -p adv < 00-setup.sql
mysql -u root -p adv -t < 02-window-functions.sql

# PostgreSQL
createdb adv
psql -d adv -f 00-setup.sql
psql -d adv -f 02-window-functions.sql
```

Scripts `04` and `06` use `DELIMITER` (MySQL) or `$$` (PostgreSQL), so run them with the command line client rather than pasting them into a tool that splits on semicolons. The `EXPLAIN` parts create a 20,000-row table called `big` and drop it again.
