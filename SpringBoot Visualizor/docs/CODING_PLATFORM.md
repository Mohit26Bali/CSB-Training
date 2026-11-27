# Coding practice (Phase F3)

Learners write real Java, run it against sample tests, and submit it against the full test suite, hidden tests included. This phase is the
**backend workflow only**: one pilot problem, the learner API, persistence and progress. There is no editor or other UI yet, and no teacher
authoring. Coding practice is separate from the Challenge Arena (multiple choice): it shares users and topics with it and nothing else.

SpringLab stays a modular monolith. Learner code is **never** run in the Spring Boot JVM. It is compiled and run by the standalone
`judge-worker` inside a sandbox (see `judge-worker/README.md` and the F2a/F2b notes at the end of this file).

## Architecture and execution flow

```
HTTP  POST /api/coding/problems/{slug}/submit   {"sourceCode": "..."}
  │  Spring Security: JWT, any role
  ▼
CodingController ──► CodingService
  1. slug → problem (404 if unknown or inactive)
  2. validate the source: not empty, ≤ 64 KiB, valid UTF-8, no NUL (400)
  3. read the CURRENT version's tests from the database (visible only for Run, all for Submit)
  4. ExecutionService.submit(user, ExecutionRequest{ problem, mode, source, tests })
        ├─ backend available? (503)   one in-flight job per user? (409)   workers + queue full? (429)
        └─ ExecutionBackend (DisabledBackend by default, or WslBackend)
  5. WslBackend: wsl.exe --exec <jdk>/bin/java judge.Main submit --framed --job <id> -
        stdin: LEN n + source, then TESTS k + k test lines (inputs AND expected values)
  6. judge-worker driver (trusted, outside the sandbox):
        compiles and runs the learner code inside bubblewrap + a cgroup scope,
        sends only each test's INPUT into the sandbox, compares the OBSERVATION that comes back
        with the EXPECTED value it keeps, and prints one JSON line
  7. WorkerResponseParser validates that line strictly; CodingService reconciles it with the tests that were sent
  8. Submit only: one transaction stores the submission, its per-test results and the progress change
  ▼
201 {id, verdict, passed, total, visibleTests, hiddenTotal, hiddenPassed, compilerOutput, feedback, ...}
```

* The request is synchronous: the POST returns when judging has finished (normally 2–6 s; about 10 s extra for the first job after WSL
  was stopped; at most the hard timeout, 60 s by default, plus a margin).
* **No database transaction is open while code runs.** The submission is written afterwards, in one short transaction.
* Run and Submit share the same admission control (per-user in-flight limit, global workers and queue), so a run cannot be used to bypass it.

## Run versus Submit

| | Run `POST …/run` | Submit `POST …/submit` |
|---|---|---|
| Tests judged | the **visible** (sample) tests of the current version | **all** tests, hidden ones included |
| Stored | nothing | the submission (source, verdict, counts), one result row per test |
| Progress | **never** changes | changes (see below) |
| Response | verdict, visible test results with messages, compiler output, feedback | adds submission id, version, hidden pass/fail **counts** |
| HTTP status | 200 | 201 with a `Location` header |

## Verdicts

| Verdict | Meaning | Counts as an attempt |
|---|---|---|
| `ACCEPTED` | every test passed (the worker said so, judged exactly the tests that were sent, and each passed) | yes |
| `WRONG_ANSWER` | the code ran but at least one test failed. An exception thrown by `execute` is *observed behaviour*, so it fails the tests that expected something else; it is not a crash | yes |
| `COMPILE_ERROR` | the source did not compile, or declares a class other than `RetryExecutor`, or lacks the required method | yes |
| `RUNTIME_ERROR` | the program ended abnormally (for example `System.exit`) before the tests finished | yes |
| `TIME_LIMIT` | a test or the whole run did not finish in time, or the hard timeout passed | yes |
| `MEMORY_LIMIT` | out of memory (heap or the memory limit of the sandbox) | yes |
| `OUTPUT_LIMIT` | printed more than the output cap | yes |
| `INTERNAL_ERROR` | **our** failure: a worker crash, a malformed or inconsistent worker answer, an unreadable test, an invalid worker verdict | no (stored, not counted) |
| `CANCELLED` | cancelled (shutdown or an explicit cancel); whatever the sandbox returned is discarded | no (stored, not counted) |

Anything that is not a clear and consistent answer becomes `INTERNAL_ERROR`. In particular `ACCEPTED` is produced only if the worker said
`ACCEPTED`, reported the same number of tests with the same visibility as were sent, and every one of them is `PASS`. A cancelled, timed-out,
crashed or interrupted submission can never be `ACCEPTED`.

## The pilot problem: Retry with bounded backoff

Slug `retry-with-bounded-backoff`, topic `microservices`, difficulty MEDIUM, Java 17. The learner implements

```java
public static <T> T execute(Callable<T> operation, int maxAttempts, long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) throws Exception
```

* `maxAttempts` counts the first call; `< 1` throws `IllegalArgumentException` before calling `operation`.
* A `TransientException` is retried; the wait before retry *k* is `min(base × 2^(k-1), max)`, computed without overflow, through `sleeper.sleep`.
* No wait after the last failure; when attempts run out the **last** `TransientException` is rethrown (the same object).
* Any other exception propagates at once, without retry or wait.

`Sleeper` and `TransientException` are provided; the source may declare only `RetryExecutor` (and nested classes). Tests inject a recording
`Sleeper`, so **no test sleeps and none depends on the clock**. The problem has 12 tests: 3 visible (basic flow, backoff sequence, exhaustion)
and 9 hidden (delay cap, non-transient exception, `maxAttempts = 1`, no sleep after the last failure, `maxAttempts < 1`, overflow, a
non-transient failure after a transient one, success on the last attempt, base above the cap).

## Database (Flyway V11)

```
topics (existing) 1───* coding_problems 1───* coding_problem_versions 1───* coding_test_cases
                              │                        │                          │
                              │                        │                          └──* coding_submission_results
users (existing) ───*  coding_submissions ─────────────┘(problem_version_id)  ─────┘
        └──────────*  coding_progress  (unique user_id + problem_id)
```

| Table | Purpose |
|---|---|
| `coding_problems` | slug (unique), topic, title, difficulty, language, `harness_kind` (which fixed worker harness judges it), `current_version`, active |
| `coding_problem_versions` | **immutable** content of one version: statement, objectives, constraints, examples, starter code. Unique `(problem_id, version)` |
| `coding_test_cases` | the tests of one version, `visible` or hidden, with `input_spec` (the scripted scenario) and `expected_spec` (the expected observation), both JSON |
| `coding_submissions` | one row per **full** submission: user, problem, **version**, source, SHA-256, verdict, passed, total, compiler output, judging time |
| `coding_submission_results` | one row per test per submission: status, and a message only for a visible test |
| `coding_progress` | one row per user and problem: attempts, accepted count, first accepted submission and time (set once), latest submission and verdict |

**How history stays interpretable when a problem changes.** A problem row only names its *current* version. A version, with its statement,
starter code and tests, is never edited after it exists; changing a problem means inserting a new version (with its own test rows) and moving
`current_version`. Every submission stores the `problem_version_id` it was judged against, and every result row points at the exact test of
that version, so an old verdict, its counts and its per-test results always read exactly as they were. A version that a submission refers to
cannot be deleted (foreign key). A migration test and an API test cover this. Progress is the only mutable summary, and it is derived only from full submissions.

**Progress** (`GET /api/coding/progress/me`) is computed from stored full submissions. `attempts` counts judged submissions (every verdict except
`INTERNAL_ERROR` and `CANCELLED`); sample runs are never stored and never counted. A problem is *accepted* once (`accepted_at` and
`first_accepted_submission_id` are set by the first `ACCEPTED` and never overwritten); later accepted submissions raise `attempts` and
`accepted_submissions` but not the number of accepted problems. The progress row is changed with one atomic statement in the same transaction
as the submission, so the two cannot disagree.

The seed (the pilot problem, its version and its 12 tests) is inserted by the migration itself. The expected observation of every test was computed
from a model of the exercise, and a test re-computes all 12 with an independent Java model and compares.

## API (`/api/coding`, any authenticated role)

| Method and path | Result |
|---|---|
| `GET /topics` | topics that have active coding problems, with a count |
| `GET /problems?topic=&difficulty=` | active problems with the caller's own `accepted` and `attempts` |
| `GET /problems/{slug}` | statement, objectives, constraints, examples, starter code, **sample tests in words**, and a **count** of hidden tests |
| `POST /problems/{slug}/run` `{"sourceCode"}` | judges the sample tests; nothing stored |
| `POST /problems/{slug}/submit` `{"sourceCode"}` | judges all tests; stores; updates progress |
| `GET /problems/{slug}/submissions` | the caller's own submissions for the problem, newest first (up to 50) |
| `GET /submissions/{id}` | the caller's own submission, with the source they sent |
| `GET /progress/me` | problems attempted and accepted, total full submissions, latest verdict per problem |

The only thing a learner sends is `sourceCode`. The problem is the slug in the URL; the tests, mode and harness are chosen by the server.
Extra JSON fields (tests, a problem key, a mode) are ignored, and a test proves it.

Errors use the existing problem-detail format: 400 invalid source or body (empty, over 64 KiB, not UTF-8, NUL, unknown difficulty or topic),
401 no token, 404 unknown or inactive problem and any submission that is not yours, 409 you already have a submission waiting or running,
429 the code runner is full, 503 code execution is not available.

Example (values shortened):

```
POST /api/coding/problems/retry-with-bounded-backoff/submit
Authorization: Bearer <token>
{"sourceCode": "import java.util.concurrent.Callable;\npublic final class RetryExecutor { ... }"}

201 Created   Location: /api/coding/submissions/42
{
  "id": 42, "problemSlug": "retry-with-bounded-backoff", "problemVersion": 1,
  "verdict": "WRONG_ANSWER", "passed": 9, "total": 12,
  "visibleTests": [
    {"name": "Returns the first successful result", "status": "PASS", "message": null},
    {"name": "Retries transient failures with doubling delays", "status": "PASS", "message": null},
    {"name": "Gives up after maxAttempts and rethrows the last failure", "status": "PASS", "message": null}
  ],
  "hiddenTotal": 9, "hiddenPassed": 6,
  "compilerOutput": "",
  "feedback": "All sample tests pass, but 3 hidden test(s) fail. Hidden tests check the rules in the statement, including limits and unusual inputs. Re-read each numbered rule and check it against your code.",
  "submittedAt": "2026-11-04T10:15:30.123456Z"
}
```

A failing visible test returns its message, for example `expected waits [100, 200] but got [100]`. A compile error returns the compiler output with
file and line only (`RetryExecutor.java:7: error: ';' expected`), never a host path. Execution time and memory are **not** returned: the only
measurements the worker has include the compiler and JVM start-up of the sandbox, so they are not the learner's runtime and would mislead. The
judging time is stored for operations only.

## Ownership and authorization

* Every endpoint needs a valid JWT; there is no role restriction, so students, teachers and admins can all practise.
* Reads of a submission, the history and the progress are filtered by the caller's user id **in the SQL**. Another user's submission id, an
  unknown id, and an id of an admin's submission asked for by a student all give the same 404 with the same body. Even an administrator
  cannot read someone else's source through this API.
* Administration of problems is not part of this phase.

## Hidden tests: what is protected and what is not

* Hidden test **inputs, expected values and names** are rows in `coding_test_cases` that only the repository's test reader returns, to the service,
  which passes them to the worker. No learner-facing DTO has a field that could carry them; hidden tests appear only as counts. A message from
  the worker is dropped for a hidden test twice (parser and service), the stored result row has no message for it, and tests assert that no hidden
  name, input or value appears in any response or log line.
* The **expected values never enter the sandbox**. The trusted driver sends only each scenario's *input* and compares the observation itself, so
  learner code cannot read an expected value from its own process, memory or files (tested with a sentinel search of the whole sandbox filesystem).
* Test data never appears in a `toString`, exception message or log line, and the learner source is never logged (only its length and a short hash).

Limits, stated plainly:

* The learner's code and the harness that feeds it inputs **share one JVM** inside the sandbox. A learner can read the *inputs* of the tests it is
  run against, and could forge the harness's answers (the per-run token is reachable by reflection). Forging helps only someone who already knows
  the expected answer, which is the solution. The pass/fail pattern across repeated submissions is also a very low-bandwidth channel.
* The visible tests deliberately show their expected values.
* This is an educational platform, not a high-stakes examination system. Hidden tests are protected from the API, from logs and from the
  learner's process, **not cryptographically** from a determined attacker with unlimited submissions.
* The WSL sandbox is a controlled local and classroom backend, not a public multi-tenant sandbox (see `judge-worker/README.md`).

## Configuration and start-up

Execution is **disabled by default**; the application starts normally without WSL, a JDK or a worker, and nothing contacts WSL at start-up
(availability is checked lazily and cached). Every code-running endpoint then answers 503.

To enable it on a Windows development machine with Ubuntu WSL:

1. In Ubuntu, install a Linux JDK 17 in `~/springlab-judge/jdk17` (a user-space Temurin tarball; see `judge-worker/README.md`) and have `bubblewrap`,
   `systemd` user scopes and `python3` available.
2. Build the worker, from inside Ubuntu: `bash /mnt/<drive>/…/judge-worker/build.sh`.
3. Start the backend with the usual environment (`JWT_SECRET`, `DB_USERNAME`, `DB_PASSWORD`; never in a file) plus:

   | Property | Environment variable | Default |
   |---|---|---|
   | `springlab.coding.execution.backend` | `SPRINGLAB_CODING_EXECUTION_BACKEND` | `DISABLED` (set `WSL`) |
   | `springlab.coding.execution.wsl.distro` | `SPRINGLAB_CODING_EXECUTION_WSL_DISTRO` | none (for example `Ubuntu`) |
   | `springlab.coding.execution.wsl.base-dir` | `SPRINGLAB_CODING_EXECUTION_WSL_BASE_DIR` | none (for example `/home/you/springlab-judge`) |
   | `springlab.coding.execution.wsl.executable` | | `wsl.exe` |
   | `springlab.coding.execution.max-workers` | | `4` (1–4) |
   | `springlab.coding.execution.queue-capacity` | | `16` (1–100) |
   | `springlab.coding.execution.hard-timeout-seconds` | | `60` (5–300) |

   **Git Bash on Windows** rewrites environment values that look like POSIX paths (`/home/you/...` becomes `C:/Program Files/Git/home/you/...`),
   which the validation then (correctly) refuses. Set these variables from PowerShell or cmd, or start Git Bash with `MSYS_NO_PATHCONV=1` and
   `MSYS2_ENV_CONV_EXCL="SPRINGLAB_*"`. When execution is unavailable the application logs the reason once per distinct reason, for the
   operator only; a learner is only told that code execution is not available.

   Values are validated against strict patterns before they reach a command line; a bad value makes execution unavailable. An invalid limit
   or an unknown backend name stops the application at start-up; there is no setting that runs code in-process.

Flyway applies `V11` on start-up (`V1`–`V10` are unchanged).

## Running the pilot end to end

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
        -d '{"username":"<you>","password":"<password>"}' | python -c 'import sys,json;print(json.load(sys.stdin)["accessToken"])')
curl -s localhost:8080/api/coding/problems/retry-with-bounded-backoff -H "Authorization: Bearer $TOKEN"        # statement, starter code, samples
curl -s -X POST localhost:8080/api/coding/problems/retry-with-bounded-backoff/run    -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' --data @solution.json                                                  # sample tests only
curl -s -X POST localhost:8080/api/coding/problems/retry-with-bounded-backoff/submit -H "Authorization: Bearer $TOKEN" \
     -H 'Content-Type: application/json' --data @solution.json                                                  # all tests, stored
curl -s localhost:8080/api/coding/progress/me -H "Authorization: Bearer $TOKEN"
```

`solution.json` is `{"sourceCode": "<your RetryExecutor.java as a JSON string>"}`.

## Tests

```bash
cd backend && mvn test        # real MySQL; the WSL tests run when the worker is installed and are skipped, with the reason, otherwise
```

* `CodingApiIntegrationTest`: contract, secrecy, ownership, progress arithmetic, versions, queue and cancellation (the worker's answer is scripted; everything else is real).
* `CodingRealWorkerIntegrationTest`: HTTP → database → real WSL sandbox → verdicts and progress, with nothing scripted.
* `CodingMigrationTest`: V11 from an empty database and from V10, constraints, seed data against an independent model.
* `com.springlab.coding.execution.*`: admission, queue, process handling, protocol (including malformed test blocks), and the real worker.
* The sandbox itself: `judge-worker` adversarial suite (39 checks), see its README.

## What F3 did not include (F4 added the UI, F5 the bank below)

No editor or other UI, no teacher authoring or editing of problems, no second problem, no other language, no Spring/JDBC/JPA exercises, no
draft saving, no hints, no leaderboard integration, no change to the Arena, and no change to the sandbox security model.

---

# Phase F5: the problem bank (five more Java problems)

The `microservices` topic now holds **six** coding problems: the pilot above (unchanged, version 1, its tests and every learner's history
untouched) and five new ones, all served by the same tables, APIs and screens.

| Slug | Difficulty | Class the learner writes | Harness kind | Tests (visible + hidden) |
|---|---|---|---|---|
| `retry-with-bounded-backoff` | MEDIUM | `RetryExecutor` | `RETRY_EXECUTOR` | 3 + 9 |
| `circuit-breaker-state-machine` | MEDIUM | `CircuitBreaker` | `CIRCUIT_BREAKER` | 3 + 10 |
| `bulkhead-concurrency-limiter` | MEDIUM | `Bulkhead` | `BULKHEAD` | 3 + 9 |
| `idempotency-key-handler` | MEDIUM | `IdempotencyHandler` | `IDEMPOTENCY` | 3 + 8 |
| `saga-compensation-coordinator` | HARD | `SagaCoordinator` | `SAGA` | 3 + 8 |
| `transactional-outbox-relay` | HARD | `OutboxRelay` | `OUTBOX` | 3 + 10 |

## The contracts (what each problem asks, and what is simplified)

All five run **deterministically in memory**. None of them is a real distributed system, and none claims to be one: there is no network,
no database, no broker, no real clock and no real sleeping. Every statement says so in its own section on what is simplified.

* **Circuit breaker.** `allowRequest()`, `recordSuccess()`, `recordFailure()`, `state()` (`CLOSED`/`OPEN`/`HALF_OPEN`). Consecutive failures
  trip it; after the open duration (boundary inclusive) exactly one trial is allowed; a successful trial closes, a failed one reopens with a
  **fresh** timer; `state()` is a pure read. *Simplified:* an injected clock replaces real time, one breaker guards one imaginary dependency,
  no sliding windows or metrics.
* **Bulkhead.** `execute(Callable)`, `inFlight()`, `availablePermits()`; rejects with `BulkheadFullException` immediately, never leaks a permit
  on success, exception or error, and re-throws the operation's own exception. *Simplified:* the semaphore pattern inside one JVM. The
  tests use real threads held with latches so outcomes are deterministic, with short timeouts. No claim about production thread-pool sizing
  or fairness.
* **Idempotency key handler.** `handle(key, Callable)`: run once per key, return the stored result for a repeat, throw
  `RequestInProgressException` for a duplicate that arrives while the first still runs, leave nothing behind on failure (so it can be retried),
  and expire keys after a TTL. *Simplified:* in-memory map, injected clock. "Still running" is simulated by a nested `handle` call from inside
  the running operation, not by a second thread. No persistence and no cross-node coordination.
* **Saga compensation.** `run()` returns `SagaStatus` (`COMPLETED`, `COMPENSATED`, `COMPENSATION_FAILED`): run steps in order, stop at the
  first failure, compensate only the **completed** steps in **reverse** order, keep compensating when one compensation fails, report which
  need manual recovery, refuse to run twice. *Simplified:* steps are local objects; no services, no network, no cross-service transaction.
* **Transactional outbox relay.** `relayBatch(int maxEvents)` plus an idempotent `IdempotentConsumer`: publish in order, mark published only
  **after** delivery, stop the batch at the first failure, accept at-least-once duplicates and ignore them by id in the consumer.
  *Simplified:* the store and publisher are in-memory fakes whose failures the test scripts. No Kafka, no RabbitMQ, no real database
  transaction. The atomicity of "business change + outbox row" is explained, not simulated.

## Schema and migration

`V12__coding_problem_bank.sql`. **No new table or column.** The only schema change is the CHECK `ck_coding_problems_harness`, which allowed
one value and now names the six harness kinds (still a closed list: anything else is refused by the database). For each new problem the
migration inserts a `coding_problems` row, version 1, and its tests. It never updates or deletes a row of the pilot. A test case of the new
problems stores a **script** in `input_spec` (`{"script": ["new 2 100", "fail", ...]}`) and the **expected observation of every operation** in
`expected_spec` (`{"trace": ["-", "-", ...]}`). The expected traces were computed by independent Python models of each exercise, and the Java
reference solutions that run in the real sandbox must reproduce them (two separate implementations agreeing).

## Execution: one generic scripted harness, chosen by the backend

There is no separate execution path per problem. The security model (WSL + bubblewrap + cgroup scope, seccomp, no network, limits) is the one
described above and is **unchanged**.

1. `CodingService` reads the problem's `harness_kind` from the database. A learner request carries only `sourceCode`; the harness, the class
   and the tests can never come from it.
2. `TestSpecs.toWorkerTest` turns each stored row into a `ScriptTestSpec` (script + expected trace) and validates it against the exercise's own
   operation allow-list, an alphabet and length limits, and "one expected line per operation". A row that fails this stops the run as
   `INTERNAL_ERROR` **before** the worker is asked.
3. `WslBackend` starts the worker with `--harness <KIND>` and sends `SCRIPT` lines (fields base64url-encoded).
4. The worker validates again, independently (kind known, operations in that kind's list, characters, sizes). Anything malformed is rejected
   and cannot produce `ACCEPTED`.
5. Inside the sandbox a small **trusted adapter** for that kind (`judge-worker/rt/*Adapter.java`) executes one operation per script line on the
   learner's class and prints one observation per operation (exceptions become `EX:<SimpleName>`). Only the **script** enters the sandbox.
6. The trusted driver, outside the sandbox, compares the observations with the expected trace it kept, and for a visible test names the first
   difference (`step 7 (allow): expected true but got false`). For hidden tests the learner sees only counts.

`CodingService` still reconciles the answer: `ACCEPTED` only if the worker said so, judged the same number and visibility of tests, and every
one passed. This is checked for the scripted problems too.

## Hidden data

Unchanged rules: hidden tests have no description anywhere; their names, scripts and traces are never in a response, a log line or a stored
message. Two hidden test names that repeated a rule sentence of a statement were renamed before release so that "no hidden test name appears
in anything a learner receives" holds for the whole bank. Visible tests are shown as numbered operation lists with the numbered expected
observations, which is the intended learning material.

## Frontend

No new endpoint, route or screen. The workspace takes the class name for the editor hint from the starter code (it used to be the literal
`RetryExecutor`), and shows multi-line sample inputs, expected observations and examples with their line breaks (`whitespace-pre-wrap`).

## Tests added in F5

* `CodingBankMigrationTest`: V12 from an empty database and from a V11 database holding learner history; the pilot's problem, version, tests
  and submissions are identical afterwards (digest comparison); every stored script validates for its own exercise; the widened CHECK still
  refuses an unknown kind.
* `ScriptTestSpecTest`: per-exercise operation lists, alphabet, length, trace size, name, tests of the wrong exercise, `toString` secrecy.
* `CodingBankApiIntegrationTest` (scripted worker answer): catalogue and detail of all six problems, the harness picked from the database even
  against forged request fields, run = visible only, submit = all, progress, hidden secrecy, unreadable stored tests stop the run, inconsistent
  worker answers are never `ACCEPTED`, ownership, the pilot unchanged.
* `CodingBankRealWorkerIntegrationTest` (real WSL sandbox): every reference solution is `ACCEPTED` on all tests; every wrong solution (21, in
  `backend/src/test/resources/coding/bank/<KIND>/`) is `WRONG_ANSWER`; compile errors per problem; a wrongly shaped class; an infinite loop; the
  pilot still `ACCEPTED`.
* `WslExecutionIntegrationTest.aMalformedScriptedTestsBlockIsRejectedByTheWorker...`: 21 malformed scripted blocks refused by the worker (16 in F5, 5 more in F6).
* Frontend: class-name hint, multi-line samples and examples, hidden-test count, `starterClassName`.
* Assertions updated because "one problem" became "six": counts in `CodingApiIntegrationTest` and `CodingMigrationTest`, the exact worker
  command line in `WslBackendProcessHandlingTest` (now includes `--harness RETRY_EXECUTOR`), and the type of `ExecutionRequest.tests()` in
  `TestSpecTest`. No assertion was weakened or removed.

## Remaining limitations

* Bulkhead tests use real threads and short waits; on a heavily loaded machine a timeout is possible (reported as a wrong result, never as an
  acceptance). The other problems have no timing dependence.
* The scripted harness only observes what the adapter exposes. A solution can pass every test and still be wrong in a way no test observes.
* One class per problem, one file, standard library only (as in F1 to F4).
* Real-browser acceptance of the UI is still outstanding.

## Sandbox suite stability (follow-up to F5)

One full-suite run once gave 38/39. It was reproduced under heavy CPU load (12 busy loops) and traced to the suite, not the sandbox:
`H01` (a whole-filesystem walk) was cut off by the 3 s per-test limit, so it now has a 15 s limit with unchanged assertions; `D04`
(fork bomb) also accepts `unable to create native thread`, the same TasksMax limit seen one step earlier; a stale job directory left by
a hard-killed run (not by the sandbox) made `Z01` fail once. After the changes: 39/39 on 5 idle runs and 4 runs under load; the bulkhead
problem (reference and 3 wrong solutions) was correct on 8 of 8 runs under the same load. The bulkhead adapter's 800 ms waits are still
a theoretical risk on a much slower machine (a false wrong answer, never a false acceptance).

---

# Phase F6: the easy tier (ten everyday problems)

The catalogue now has **sixteen** problems. Ten new ones are in the style of the training exercises: small business rules, plain Java, a clear
rule list, no framework. They are in two topics that had no coding problems before, **Java Core** (`java-core`) and **Spring Boot Basics**
(`spring-boot-basics`). Nothing earlier changed: the pilot and the five microservices problems, their tests and every learner's history are the
same (checked by a digest comparison in the migration test).

| Slug | Topic | Difficulty | Class the learner writes | Tests (visible + hidden) |
|---|---|---|---|---|
| `password-validator` | Java Core | EASY | `PasswordValidator` | 3 + 5 |
| `loan-eligibility` | Java Core | EASY | `LoanEligibility` | 3 + 6 |
| `salary-calculator` | Java Core | EASY | `SalaryCalculator` | 3 + 6 |
| `bank-account` | Java Core | EASY | `BankAccount` | 3 + 5 |
| `training-registry` | Java Core | EASY | `TrainingRegistry` | 3 + 7 |
| `grade-calculator-fix` ("fix the bug") | Java Core | EASY | `GradeCalculator` | 3 + 4 |
| `request-validator` | Spring Boot Basics | EASY | `RequestValidator` | 3 + 5 |
| `product-service-crud` | Spring Boot Basics | MEDIUM | `ProductService` | 3 + 7 |
| `exception-to-http-status` | Spring Boot Basics | EASY | `ErrorMapper` | 3 + 5 |
| `bean-registry-primary` | Spring Boot Basics | MEDIUM | `BeanRegistry` | 3 + 8 |

What each teaches: rules in a fixed order and custom checked exceptions (password); boundaries and ordering (loan); calculation by type and
input validation (salary); object consistency and a checked exception (bank account); collections, duplicates, capacity, sorted and ordered
results (registry); reading code and finding three kinds of bug (grade); what `@Valid` does behind the annotations (request validation); what a
service layer does over a repository (CRUD); what a `@RestControllerAdvice` decides and why internal messages are never shown (error mapping);
how Spring chooses between beans, `@Primary` and `@Qualifier` (bean registry).

## What is simplified (and said so in each statement)

Plain Java, no framework, no database, no network. The Spring-flavoured problems implement the **decision logic** that Spring performs
(validation rules, a service over an in-memory `Map`, status mapping, bean resolution); they do not run a controller, a repository or an
application context. Real Spring behaviour is still tested by the multiple-choice Challenge Arena.

## Execution: one generic reflective adapter

No new execution mechanism and no change to the security model. The F5 scripted harness is reused, with one more adapter,
`ReflectiveAdapter`, shared by all ten problems:

* a script is a list of `new ARG...` (build the learner's object) and `call METHOD ARG...` (call one of its public methods);
* arguments are converted to the parameter type (`int`, `long`, `double`, `boolean`, `char`, `String`; `empty` is `""`, `null` is null, `_` is a
  space); only methods the learner's class **declares** can be called (never `getClass` and the like);
* the observation is the returned value as one line (a `double` with two decimals, a list as its items joined by commas and `-` when empty,
  `-` for `void`) or `EX:ExceptionName`;
* the contract of each exercise (class, constructors, methods and their types) is a fixed entry in `judge-worker/rt/Adapters.java`, checked
  before any test runs. A class that does not match is a `COMPILE_ERROR` that lists the missing public members;
* provided types the learner may use: `PasswordValidationException`, `InsufficientBalanceException` (checked), `ProductNotFoundException`,
  `DuplicateProductException`, `NoSuchBeanException`, `NoUniqueBeanException` (unchecked).

The only change to the script rules: `@` is allowed in script and trace text (e-mail addresses are data). Everything else outside the alphabet is
still refused by the backend **and** by the worker, and a script may only use the operations of its exercise (`new`, `call`).

## Schema and migration

`V13__coding_easy_tier.sql`: no new table or column. It widens the CHECK `ck_coding_problems_harness` to the sixteen kinds and inserts ten
problems (version 1) with their tests. Expected traces were computed by independent Python models; the Java reference solutions run in the
real sandbox must reproduce them. (MySQL compares the CHECK list case-insensitively; the backend matches kinds case-sensitively and refuses to
judge a problem whose kind it does not know, so a mis-cased row could never be run.)

## Frontend

The catalogue gained **Topic** and **Difficulty** filters (client side, from the data the API already returns), shows easier problems first, and
says how many problems are shown. With a single problem no filters are shown.

## Tests added in F6

* `CodingEasyMigrationTest`: V13 from an empty database and from a V12 database with learner history on the pilot and on a microservices
  problem (digest of everything earlier unchanged); every stored script is valid for its exercise and starts with `new`; the CHECK names all
  kinds and still refuses others.
* `CodingEasyApiIntegrationTest` (scripted worker answer): topics and counts, filters, detail pages, the harness chosen from the database even
  against forged request fields, run = visible only, hidden secrecy, inconsistent worker answers are never `ACCEPTED`, a broken stored test
  stops the run before the worker is asked.
* `CodingEasyRealWorkerIntegrationTest` (real WSL sandbox): all 10 reference solutions `ACCEPTED` on every test; all 42 wrong solutions
  (`backend/src/test/resources/coding/easy/<KIND>/wrong-*.java`, one targeted defect each; for the "fix the bug" problem the unchanged starter
  code is one of them) `WRONG_ANSWER`; a class with missing members and a syntax error are `COMPILE_ERROR`; an exception thrown by learner
  code is observed behaviour; an infinite loop is `TIME_LIMIT`.
* `ScriptTestSpecTest` and `WslExecutionIntegrationTest`: the new operation lists, the `@` rule, and five more malformed scripted blocks
  refused by the worker.
* Frontend: filters, ordering, counts, empty result and reset, topics and difficulties offered.
* Assertions updated because the number of problems changed (6 to 16, 5 to 15, the microservices-only difficulty count): no assertion was
  weakened or removed.

## Limits of this tier

* A solution can pass every test and still differ from the rules in a way no test observes.
* The learner contract is exact: method names, parameter types and return types must match the statement (otherwise `COMPILE_ERROR` with the list).
* Still plain Java in the local WSL development sandbox, not a production multi-tenant boundary; browser acceptance is still outstanding.
