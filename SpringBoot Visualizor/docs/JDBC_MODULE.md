# JDBC Lab module (Phase 8)

Route: `/learn/jdbc`. Flow: Understand → Visualize → Experiment → Observe → Explain → Test.
Built on the learning framework (`LEARNING_FRAMEWORK.md`) and the capture infrastructure of the earlier modules. It adds 27 LIVE
experiments that run real JDBC, Spring JDBC and Hibernate against the local MySQL database.

## Learning objectives

1. JDBC fundamentals: `DriverManager` vs `DataSource`, driver discovery, what `close()` means for a pooled connection.
2. `Statement`, `PreparedStatement`, `ResultSet`, the `execute*` methods, resource closing, and why SQL built by concatenation is unsafe.
3. CRUD: row counts, generated keys, reading many rows, batches.
4. Transactions on two real connections: auto-commit, commit, rollback, rollback on failure, savepoints, isolation levels.
5. Connection pooling with a dedicated HikariCP pool: reuse, several connections, exhaustion and recovery.
6. Spring JDBC: `JdbcTemplate`, `NamedParameterJdbcTemplate`, exception translation, `TransactionTemplate`.
7. JDBC vs Spring JDBC vs JPA/Hibernate on the same data, and the fact that all three end at the same JDBC connection.

## What the learner can do

* **Understand**: seven 5W1H explanations (one per topic).
* **Visualize**: a SIMULATED step walkthrough per topic on a diagram (architecture, statement, transaction, pool, spring, compare), and the
  JDBC / Spring JDBC / JPA comparison table. Nothing in these reports an observation.
* **Experiment**: 27 predefined LIVE experiments in seven groups, plus five explanations that are deliberately not run (SIMULATED).
* **Observe**: replay with Play / Pause / Next / Back / Reset / speed, the explanation panel, SQL inspector, code inspector, a
  statement counter, a transaction-state strip (connection A) and a pool-metrics strip. Every step carries its provenance badge.
* **Explain**: takeaways linked to their evidence, the lab tables read from MySQL, assumptions and limitations.
* **Test**: the `jdbc` Challenge Arena topic (29 questions).

## Architecture (`com.springlab.lab.jdbc`)

```
POST /api/lab/jdbc/experiments/{id}/run      (any signed-in role; the id is the ONLY input, there is no body)
   ▼
LabJdbcController ─► JdbcLabService.run(id)
      1. look the id up in the fixed catalogue (lab/jdbc-lab-catalog.json) → 404 otherwise
      2. take one of 4 run slots (waits, then 409)
      3. new random run key; JdbcExperimentContext owns every connection the run opens
      4. run the predefined experiment; JdbcRunRecorder turns observations into steps
      5. finally: close connections and the lab pool, remove this run's rows by run key (child tables first),
         record the number of rows removed (cleanupRowsRemoved)
```

* `JdbcConnections` is the only class that knows the URL and credentials; nothing it knows ever leaves it.
* `JdbcCapture` wraps `Connection`, `Statement` and `PreparedStatement` with `java.lang.reflect.Proxy`. The wrapper forwards every call
  to the real driver object unchanged and records OPEN, PREPARE, EXECUTE (SQL text with placeholders, update count, failure class and
  MySQL error code) and CALL (`setAutoCommit`, `commit`, `rollback`, `setSavepoint`, `close`, ...) events. Bound values are never recorded.
* `CapturingDataSource` lets real Spring JDBC (`JdbcTemplate`, `DataSourceTransactionManager`, `TransactionTemplate`) be observed.
* The JPA path of the comparison experiments reuses the existing Hibernate statement inspector (`TraceContext`).

## Schema and migrations

* `V6__jdbc_lab.sql`: `jdbc_lab_customers`, `jdbc_lab_products`, `jdbc_lab_accounts`, `jdbc_lab_orders` (FKs to customers and products),
  `jdbc_lab_transactions` (ledger, FK to accounts), `jdbc_lab_batch_records`. Every table has `run_key` and `created_at`.
* `V7__jdbc_questions.sql`: the Arena topic `jdbc` and 29 questions (correct answer positions 7/8/7/7).

## Experiments (27, all LIVE)

| Group | Experiments |
|---|---|
| Fundamentals (3) | jdbc-connection-lifecycle, jdbc-drivermanager-vs-datasource, jdbc-driver-discovery |
| Statements (5) | jdbc-statement-select, jdbc-prepared-select, jdbc-sql-injection, jdbc-execute-methods, jdbc-resource-closing |
| CRUD (3) | jdbc-crud-single, jdbc-read-many, jdbc-batch-insert |
| Transactions (6) | jdbc-autocommit, jdbc-commit, jdbc-rollback, jdbc-rollback-on-failure, jdbc-savepoint, jdbc-isolation |
| Pooling (3) | pool-acquire-release, pool-multiple, pool-exhaustion |
| Spring JDBC (4) | spring-jdbc-query, spring-jdbc-named-update, spring-jdbc-exception-translation, spring-jdbc-transaction |
| Compare (3) | compare-read, compare-insert, compare-same-session |

The SQL-injection experiment is LIVE for the parameterised path; the unsafe string construction is shown as CONCEPTUAL text and
is never executed.

## API

* `GET /api/lab/jdbc/catalog`: groups, experiments (question, claim, diagram), diagrams, limitations.
* `GET /api/lab/jdbc/mapping`: the lab tables from `information_schema`; no URL, host, port or credentials.
* `POST /api/lab/jdbc/experiments/{id}/run`: runs one experiment and returns steps, SQL, `sqlCount`, `cleanupRowsRemoved`, limitations.

401 without a token, 404 for an unknown id, 405 for a GET on the run route. Responses carry no bound values, credentials or URLs.

## LIVE / INFERRED / SIMULATED

* **LIVE / CAPTURED**: observed in this run: JDBC calls through the capture proxies, Hibernate's statement inspector, values read back
  from MySQL, MySQL's own per-session counters (`show session status`), `connection_id()`, pool MXBean counts.
* **INFERRED / DERIVED**: computed from observations (for example a transaction state after `commit()` returned normally).
* **SIMULATED / CONCEPTUAL**: explanation only. Carries no SQL and no evidence.

Rules the code follows: a statement is never claimed to have executed unless the driver call returned; a commit is never claimed
without `commit()` having returned; statement execution, commit, rollback and visibility from another connection are separate steps.

## SQL capture limitations

SQL shown is the text handed to the JDBC driver, with `?` placeholders. It is not proof that MySQL executed it; where that matters
the experiment also reads MySQL's counters. Connector/J prepares statements on the client by default and does not rewrite batches
(observed: `Com_insert +5` for a 5-statement batch). Bytes on the wire are not captured. Driver-internal calls are not shown.

## Transaction methodology

Transaction experiments open two real connections (A and B) from the application's pool and perform a fixed sequence of operations
on them. There is no `Thread.sleep` and nothing depends on timing. B reads to show what other sessions can see. The isolation
experiment runs REPEATABLE READ (MySQL default), READ COMMITTED and READ UNCOMMITTED; SERIALIZABLE and phantom reads are explained, not run.

## Pool isolation

Pool experiments create a dedicated bounded HikariCP pool per run (maximum 1 to 3 connections, short `connectionTimeout`) and close it
in `finally`. The application's own pool is only read (active/idle/total counts) and never touched.

## Cleanup and crash recovery

Each run removes its own rows by its random run key, child tables first, and reports the count. If the JVM dies mid-run, rows stay in
the `jdbc_lab_*` tables. At startup `JdbcLabService` (an `ApplicationRunner`) deletes only rows older than 10 minutes
(`created_at`), so a run that is active on another instance is not disturbed. Nothing outside `jdbc_lab_*` is ever touched.

## Security

The browser sends only an experiment id. No SQL, table or column name, URL or credential is accepted (tested with hostile ids and
bodies). Responses are checked for `localhost`, `3306`, passwords and authorization headers. Any signed-in role may run (same as the
JPA lab). A semaphore of four limits concurrent runs.

## Verification performed

* Backend: full suite 303 tests, 0 failures, against real MySQL (`springlab_test`), including 41 JDBC tests and the code-reference test.
* Frontend: 22 test files / 275 tests passing (18 JDBC tests among them); `oxlint` clean; production build succeeds.
* Live HTTP script against the running backend: all authorization checks, all 27 experiments (LIVE, honest provenance, no lab row
  left, application tables unchanged), claims confirmed by the server's own evidence, MySQL digest counters agreeing with the
  capture (`INSERT` count, `COMMIT`, `ROLLBACK` seen by MySQL), three rounds of 6 concurrent users with statement counts equal to the
  single-user baseline, the `jdbc` Arena topic (29 questions, no answer or explanation before submit, spread answers), and REST
  ticket handshake, JWT, JPA and admin regression checks.

## Known limitations

* Single measurements on tiny tables; no performance claim.
* Connector/J defaults only; server-side prepared statements, batch rewriting and other drivers are explained, not run.
* SERIALIZABLE, phantom reads and InnoDB locking are not run.
* Path C's commit-time JDBC calls (Hibernate flush at commit) are stated, not observed.
* Browser acceptance has not been performed by Claude (no browser available); see the checklist in the Phase 8 report.
