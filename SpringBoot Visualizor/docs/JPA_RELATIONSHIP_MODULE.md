# JPA Relationship Lab module (Phase 7)

Route: `/learn/jpa-relationships`. Flow: Understand → Visualize → Experiment → Observe → Explain → Test.
Built on the learning framework (`LEARNING_FRAMEWORK.md`) and the SQL-capture infrastructure of the other two modules
(`REST_LIFECYCLE_MODULE.md`, `JWT_SIMULATOR_MODULE.md`). It adds one new kind of diagram (entities, tables and foreign keys) and
25 LIVE experiments that run real Hibernate against the local MySQL database.

## What the learner can do

* **Understand**: eight 5W1H explanations (ORM/JPA/Hibernate, one-to-one, owning side and `mappedBy`, many-to-many, cascade and
  `orphanRemoval`, lazy and eager fetching, N+1 with JOIN FETCH and EntityGraph, persistence context with dirty checking, flush and commit).
* **Visualize**: an entity/table diagram for five relationship families, drawn from the server's description of the real mapping.
  Selecting an annotation highlights the Java field, the column, the foreign key and the connecting edges and explains it in
  plain language. A SIMULATED walkthrough shows what an `EntityManager` does with a change.
* **Experiment**: 25 predefined LIVE experiments in seven groups, plus five explanations that are deliberately not run (SIMULATED).
* **Observe**: step-by-step replay of the run with the diagram, explanation panel, SQL inspector, a statement counter and
  an entity-state strip. Every step carries its provenance badge.
* **Explain**: key points linked to their evidence, a table of every association read from the running system, assumptions and
  limitations. **Test**: the `jpa-hibernate` Challenge Arena topic (now 29 questions).

## Architecture (`com.springlab.lab.jpa`)

```
POST /api/lab/jpa/experiments/{id}/run
        │  authenticated by the application's real security chain (any signed-in role); the id is the ONLY input
        ▼
LabJpaController ──► JpaLabService.run(id)
                        1. look the id up in the fixed catalogue (lab/jpa-lab-catalog.json) → 404 otherwise
                        2. take one of 4 run slots (waits 5 s, then 409)
                        3. new run key; new TraceCollector bound to THIS thread (TraceContext)
                        4. experiment.run(ExperimentContext)   ← real EntityManager, repositories, JDBC
                        5. finally: unbind the collector, delete exactly this run's rows, release the slot
        ▼
ExperimentRecorder: live / observe / infer / concept  →  steps with provenance + the SQL Hibernate prepared during each step
```

* `Experiment`/`ExperimentSet`: one lambda per experiment, grouped in `RelationshipExperiments`, `CascadeExperiments`,
  `FetchExperiments` and `PersistenceContextExperiments`.
* `ExperimentContext`: the `EntityManager`, a `JdbcTemplate` (seeding and reading rows; Hibernate never sees these), manual transaction
  control (`begin` / `commit` / `rollback`) and `inOtherTransaction` (a second connection, to show what other transactions can see).
* `JpaMappingService` (`GET /api/lab/jpa/mapping`): the object model from the **JPA metamodel and the real annotations**, the relational
  side from **MySQL `information_schema`** (columns, primary keys, unique keys, foreign key constraints). The diagram is built from it.
* `LabJpaCatalog` (`GET /api/lab/jpa/catalog`): groups, experiments, the question each asks and the outcome it claims.
* `DepartmentRepository`: three ways to load the same departments (plain, `join fetch`, `@EntityGraph`).
* The StatementInspector, `TraceCollector` and `TraceContext` are the existing infrastructure; `TraceCollector` gained two additive
  methods (`sqlCount`, `sqlSince`) and `TraceStore.size()` became public.
* No entity is serialized: only the records in `LabJpaDtos` leave the server.

## Entity model and schema (Flyway `V4__jpa_relationship_lab.sql`)

All tables are prefixed `jpa_lab_`, carry a `run_key` column, and hold **no application data**. Real foreign keys and unique keys are
used on purpose, because several experiments show MySQL enforcing them.

| Pair | Mapping | Foreign key |
|---|---|---|
| `Account` ↔ `Profile` | `@OneToOne` / `@OneToOne(mappedBy)` | `jpa_lab_profile.account_id`, **UNIQUE** (owner: `Profile`) |
| `Department` ↔ `Employee` | `@OneToMany(mappedBy)` / `@ManyToOne(fetch = LAZY)` | `jpa_lab_employee.department_id` (owner: `Employee`), no cascade |
| `Department` ↔ `Room` | `@OneToMany(mappedBy)` (a `Set`) / `@ManyToOne(fetch = LAZY)` | `jpa_lab_room.department_id` (second collection, for the cartesian experiment) |
| `Student` ↔ `Course` | `@ManyToMany @JoinTable` / `@ManyToMany(mappedBy)` | join table `jpa_lab_enrollment (student_id, course_id)`, composite primary key (owner: `Student`), no cascade |
| `Project` ↔ `Task` | `@OneToMany(cascade = ALL)` / `@ManyToOne` (**default fetch, EAGER**) | `jpa_lab_task.project_id` |
| `Team` ↔ `Member` | `@OneToMany(cascade = ALL, orphanRemoval = true)` / `@ManyToOne(fetch = LAZY)` | `jpa_lab_member.team_id` |

Keys use `IDENTITY` (MySQL `AUTO_INCREMENT`), so `persist()` runs its INSERT immediately; an experiment shows this and the explanation says
what changes with a sequence generator. `Department.employees` is a `List` (a bag) and `rooms` a `Set`, which is what lets the
two-collection experiment run and shows the bag keeping duplicate entries.

## Experiments

LIVE means: executed on the server, against MySQL, now. Each step is CAPTURED (LIVE), DERIVED (INFERRED) or CONCEPTUAL (SIMULATED).

| Group | Experiment | What it demonstrates |
|---|---|---|
| One-to-one | `one-to-one-fk` | FK in the owning table, UNIQUE constraint, MySQL error 1062 for a second profile |
| Owning side | `owning-owner-only`, `owning-inverse-only`, `owning-helper-both` | which side writes `department_id`; stale in-memory collection; helper method |
| Many-to-many | `m2m-join-table`, `m2m-inverse-only` | join rows on the owning side, withdraw deletes only the join row, inverse side writes nothing |
| Cascade | `cascade-persist-none`, `cascade-persist-all`, `cascade-update-parent`, `remove-child-no-orphan`, `remove-child-orphan`, `remove-parent-no-cascade`, `remove-parent-cascade-all` | cascade versus orphanRemoval; FK refusal (MySQL error 1451); children deleted before the parent |
| Fetching | `lazy-not-accessed`, `lazy-in-transaction`, `lazy-after-close`, `fetch-defaults`, `eager-n-plus-one` | uninitialised wrapper, second SELECT, `LazyInitializationException`, real defaults, EAGER causing N+1 |
| N+1 | `n-plus-one`, `join-fetch`, `entity-graph`, `two-collections-cartesian` | measured 1+N, before/after counts, row multiplication |
| Persistence context | `entity-states`, `dirty-checking`, `first-level-cache` | transient/managed/detached/removed, UPDATE at flush, visibility only after commit, same instance |

SIMULATED (explained, never executed): the five cascade types (PERSIST, MERGE, REMOVE, REFRESH, DETACH, ALL), a unidirectional variant,
`MultipleBagFetchException`, what `open-in-view=true` would change, IDENTITY versus a sequence.

## What is LIVE, INFERRED and SIMULATED

| Claim shown | Provenance | Why |
|---|---|---|
| SQL text Hibernate prepared during a step | CAPTURED → **LIVE** | the StatementInspector saw it while the action ran |
| Rows read back (JDBC), `em.contains()`, ids, collection sizes, `isLoaded`, exception class names and MySQL error codes | CAPTURED | read by the experiment during the run |
| Statement totals and before/after comparisons | CAPTURED/DERIVED | computed from the captured statements, never typed |
| "Which side controls the FK", entity state names, "N+1 grows with N" | DERIVED → INFERRED | a conclusion drawn from the facts above; the step says so |
| MySQL "executes", MultipleBagFetchException, cascade types not run, sequence behaviour | CONCEPTUAL → SIMULATED | explanation only |

## SQL capture and query-count methodology

* The run binds a `TraceCollector` to its own thread (`TraceContext`). Hibernate's `StatementInspector` hands every statement to it as the
  text passed to JDBC (placeholders, no bound values). `ExperimentRecorder.live` remembers the statement count before an action and attaches
  everything prepared **during** the action to that step, in order.
* Seeding and the lab's verification reads use plain JDBC, which Hibernate does not see, so they are never counted and never pollute a step.
* Counts shown (for example "1 + N") are computed from the captured steps. No expected count appears in the application code.
* Independent checks: the integration test compares every run's captured count with **Hibernate's own `Statistics#getPrepareStatementCount()`**
  (after subtracting the one statement the caller's authentication prepares before the run starts), and `verify_phase7.py` compares selected runs with
  **MySQL's own per-schema statement digests** (`performance_schema`).
* Hibernate-prepared SQL is not proof of MySQL execution; the UI says so on every SQL block.

## API

| Endpoint | Purpose |
|---|---|
| `GET /api/lab/jpa/catalog` | groups, experiments (question + claim + diagram + focus entities), flow node ids, limitations |
| `GET /api/lab/jpa/mapping` | entities, tables (columns, keys, foreign keys), edges, facts (Hibernate version, `open-in-view`, database) |
| `POST /api/lab/jpa/experiments/{id}/run` | run one predefined experiment; **no body, query or header is read** |

All three need a valid token (any role). Unknown id → 404; at most 4 concurrent runs.

## Safety properties (each has a test)

* The client chooses an experiment id from a fixed list; it cannot send SQL, table names or entity ids.
* Every run's rows carry a random run key and are deleted by that key in a `finally`; the application also clears the lab tables at startup. No
  application table (`users`, `questions`, `attempts`, `topics`, …) is read or written.
* Runs do not use the request-trace store, so they cannot appear in or be fetched through `GET /api/lab/traces/{id}`.
* Concurrent runs by different users get the same statement counts they get alone.
* No bound SQL values, tokens, secrets or user data appear in a run.

## Technical assumptions

Spring Boot 3.5.16, Jakarta Persistence 3.1, Hibernate 6.6.x, MySQL 8.4 (InnoDB, default `REPEATABLE READ`), `ddl-auto: validate`,
`open-in-view: false`. Specification defaults quoted in the UI: `@ManyToOne`/`@OneToOne` EAGER, `@OneToMany`/`@ManyToMany` LAZY.

## Known limitations

* An inverse-side `@OneToOne` is mapped EAGER (Hibernate cannot make it lazy without bytecode enhancement); the lab does not demonstrate lazy `@OneToOne`.
* The unidirectional `@OneToMany` variant, `MERGE`/`REFRESH`/`DETACH` cascades, sequence generators and `MultipleBagFetchException` are explained, not run.
* Timings are single measurements and are not shown as benchmarks; the lab does not compare performance.
* Experiments run on a small fixed data set, so absolute numbers (6 departments, 3 employees each) are chosen for clarity.
* Runs are per request on one node; a crash can leave rows until the next startup cleanup.
* Multi-collection fetch behaviour (bag duplicates, row counts) is that of this Hibernate version.

## Test coverage

| Claim | Verified by |
|---|---|
| FK placement, unique FK, owning/inverse behaviour, helper method | `RelationshipExperimentsIntegrationTest` (13 experiments, MySQL) |
| Join table rows, withdraw deletes only the join row, inverse writes nothing | same |
| cascade PERSIST/REMOVE, orphanRemoval, no-cascade FK refusal (1451) | same |
| Lazy not accessed / in transaction / after close, defaults, EAGER N+1 | `FetchingAndPersistenceContextIntegrationTest` |
| N+1 = 1+N for two sizes, JOIN FETCH 7→1, EntityGraph 7→1, cartesian 24 rows for 16 objects | same |
| Entity states, IDENTITY immediate INSERT, dirty checking, flush vs commit visibility, first-level cache | same |
| Mapping endpoint: owning side, mappedBy, fetch, cascade, join table, real FKs, no application entity | `JpaLabInfrastructureIntegrationTest` |
| Every run: provenance, SQL only on CAPTURED, nodes/edges exist, no bound values, step SQL adds up, statement count equals Hibernate statistics, rows cleaned up | same |
| Authentication, unknown ids, 405, all roles, no injection, no application table changed, no trace created | same |
| Concurrent runs keep their own counts | same |
| Every code excerpt equals the real source (36 references) | `CodeReferenceTest` |
| Entity/table graph, highlighting, plain-language explanations | `er.test.js`, `JpaRunView.test.jsx` |
| Content: eight complete 5W1Hs, valid SIMULATED scenario, ids match the backend catalogue, entities exist | `jpaLab.test.js` |
| The API sends no body and encodes the id | `labJpaApi.test.js` |
| Live over HTTP against MySQL, including MySQL's own statement counters | `verify_phase7.py` (see the report) |
