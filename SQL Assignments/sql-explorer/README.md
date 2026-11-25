# SQL Explorer

A web app for the 15 SQL assignments next to it. Each assignment's database is built from **its own scripts**, loaded with its seed data, and can be explored in the browser:

- **Schema:** tables, columns, keys, foreign keys and CHECK constraints
- **Data:** browse every table, page by page
- **Queries:** run each of the assignment's queries and see the result
- **Scripts:** run the INSERT / UPDATE / DELETE scripts and see what every statement does, then everything is undone
- **Must fail:** run the negative tests and compare MySQL's real error with the one the assignment expects
- **Playground:** type your own SELECT

```
Browser (React)  --/api-->  Express API  -->  MySQL (sx_org_db, sx_bank_db, ... one database per assignment)
 frontend/                  backend/            built from the assignments' own .sql files by `npm run seed`
```

The backend reads the assignments' `.sql` files and READMEs directly, so the app can never drift away from what the assignments contain.

## What was checked first

Before building anything, all 15 assignments were run, every script in order, on a MySQL 8.4 server:

- 71 scripts, no unexpected error
- **108 negative tests, every one failed with exactly the MySQL error number its comment predicts** (1062 duplicate, 1048 NULL, 1452 / 1451 foreign key, 3819 CHECK, 1265 truncated value)
- on the author's own MySQL only assignment 01 (`org_db`) had been run; the other 14 databases did not exist yet

## Run it

You need Node 22+ and a MySQL 8 server (8.0.16+ for CHECK constraints).

```bash
cd backend
npm install
cp .env.example .env          # fill in ADMIN_PASSWORD and choose the two APP_* passwords
npm run seed -- --yes         # builds sx_* databases from the assignments and creates the two limited users
npm run dev                   # API on http://127.0.0.1:4100

cd ../frontend
npm install
npm run dev                   # http://localhost:5300  (proxies /api to the API)
```

For a single process: `npm run build` in `frontend`, then `npm start` in `backend` serves the built app and the API on one port (http://127.0.0.1:4100).

**It never touches your own assignment databases.** Every database it creates is called `sx_<original name>` (for example `sx_org_db`). `npm run seed` only drops and recreates `sx_*` databases.

## How it keeps your data safe

| Risk | What stops it |
|---|---|
| The browser changing data | The app's connections use two limited MySQL users. `sqlexplorer_ro` can only SELECT. `sqlexplorer_rw` can INSERT / UPDATE / DELETE but has no schema rights, and is used only by the sandbox |
| Data-changing scripts saving anything | They run in one transaction that is **always rolled back**. The scripts' own `START TRANSACTION` / `COMMIT` / `ROLLBACK` become savepoints, so they behave as written but nothing is kept. A test compares a checksum of every table before and after |
| Typing SQL into the playground | One statement, and only SELECT / WITH / SHOW / DESCRIBE / EXPLAIN. No `INTO OUTFILE`, no locking reads. Read-only transaction, 4 s time limit, at most 500 rows kept in memory |
| SQL injection through names | Table names are checked against `information_schema`; assignment, query and script ids come from a catalog built from the files. Nothing from the URL is ever pasted into SQL |
| Cross-site scripting | The UI renders values as text, the README with a Markdown renderer that never emits raw HTML, and SQL colouring never builds HTML. A content-security policy is sent too |
| Exposure on a network | The API listens on 127.0.0.1 by default, has a per-address rate limit, a 20 KB body limit, and answers errors without stack traces |
| Secrets | No default passwords anywhere. `.env` is git-ignored; `.env.example` shows what to set |

## The API

| Endpoint | Purpose |
|---|---|
| `GET /api/health` | server and database are up |
| `GET /api/assignments` | the 15 assignments with table, row, query and must-fail counts |
| `GET /api/assignments/:id` | one assignment: README, scripts, schema, queries, negative tests |
| `GET /api/assignments/:id/tables/:table?page=&pageSize=` | one page of a table |
| `POST /api/assignments/:id/queries/:qid/run` `{mode}` | run a query on the seed data, or `after-scripts` (after the data-changing scripts, rolled back) |
| `POST /api/assignments/:id/scripts/:file/run` | run a data-changing script in the sandbox |
| `POST /api/assignments/:id/negatives/run` | run all negative tests, compare expected and actual error numbers |
| `POST /api/assignments/:id/sql` `{sql}` | the read-only playground |

## Tests

| Suite | Command | What it covers |
|---|---|---|
| Backend (23) | `cd backend; npm test` | SQL splitter, catalog (15 assignments, 108 negative tests), **every one of the ~170 queries runs**, **all 108 negative tests fail as predicted**, sandbox leaves every table unchanged (checksums), playground refusals / time limit / row cap, injection attempts on table names, rate limit |
| Frontend (16) | `cd frontend; npm test` | result tables, SQL colouring (no HTML injection), error and empty states, API client |
| Browser (13) | `cd frontend; npm run e2e` | the real app against the real databases: all 15 assignments on all 7 tabs, schema, paging, queries, DML before / after, sandbox rollback, COMMIT / ROLLBACK handling, must-fail tests, playground, keyboard tabs, phone layout |

The backend and browser tests need the seeded databases and your `.env`. The browser tests start their own servers (ports 4101 and 5301) and use the installed Microsoft Edge (`PW_CHANNEL=chromium` after `npx playwright install chromium` for Chromium).

## Good to know

- For the DML assignments (04, 05, 06) the tables show the data **before** the data-changing scripts, so you can watch those scripts work. The Queries tab can run the verification queries "after the scripts" too.
- Scripts that create or alter tables (the 01 / 02 / 03 / 15 `ALTER` scripts) were already applied when the database was built; they are shown, not re-run (they cannot be rolled back).
- MySQL error numbers and wording come from your server's version, so they can differ slightly on another version.
