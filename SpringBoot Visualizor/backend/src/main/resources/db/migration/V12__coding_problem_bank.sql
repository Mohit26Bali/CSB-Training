-- Coding practice (Phase F5): five more Java problems in the Microservices topic.
--
-- Schema: nothing new. The one change is that the CHECK on coding_problems.harness_kind, which allowed only the pilot's harness, now names the
-- five new harness kinds. A harness kind selects a fixed trusted adapter in the judge worker; it is chosen from this column by the backend and
-- can never come from a request.
--
-- Versioning: the pilot (retry-with-bounded-backoff, version 1) and its tests are not touched. Each new problem is inserted as version 1 with its
-- own tests. A test here is a SCRIPT of operations on the learner's class (input_spec) and the EXPECTED OBSERVATION of each operation
-- (expected_spec); the expected traces were computed by independent models of the exercises, and a test checks them against reference
-- solutions run in the real sandbox.

ALTER TABLE coding_problems DROP CHECK ck_coding_problems_harness;
ALTER TABLE coding_problems ADD CONSTRAINT ck_coding_problems_harness CHECK (harness_kind IN ('RETRY_EXECUTOR', 'CIRCUIT_BREAKER', 'BULKHEAD', 'IDEMPOTENCY', 'SAGA', 'OUTBOX'));

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Circuit breaker state machine
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'circuit-breaker-state-machine', 'Circuit breaker state machine', 'MEDIUM', 'java17', 'CIRCUIT_BREAKER', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'microservices';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Implement the CLOSED, OPEN and HALF_OPEN states of a circuit breaker: trip after repeated failures, wait, allow one trial, then close or reopen.',
 '## Circuit breaker state machine

A **circuit breaker** protects a caller from a dependency that keeps failing. While the dependency looks healthy the breaker is **CLOSED** and lets every request through. After too many failures in a row it trips to **OPEN** and rejects requests at once, so the caller stops waiting for something that is down. After a cool-down it lets exactly **one** trial request through (**HALF_OPEN**): if the trial succeeds the breaker closes again, if it fails the breaker opens again.

The SpringLab Microservices Lab shows this in the experiment `res-circuit-breaker`; here you write the state machine yourself. This exercise is plain Java in one JVM with a fake clock. It does not call a service and it is not Resilience4j.

### Your task

Write `public final class CircuitBreaker` with exactly this public API:

```java
public final class CircuitBreaker {
    public enum State { CLOSED, OPEN, HALF_OPEN }

    public CircuitBreaker(int failureThreshold, long openMillis, Clock clock)
    public boolean allowRequest()
    public void recordSuccess()
    public void recordFailure()
    public State state()
}
```

`Clock` is provided. Do not declare it (a source that declares any top-level class other than `CircuitBreaker` is rejected):

```java
public interface Clock {
    long nowMillis();
}
```

### Required behaviour

1. **Arguments.** If `failureThreshold < 1` or `openMillis < 1`, the constructor throws `IllegalArgumentException`.
2. **CLOSED.** `allowRequest()` returns `true`. The breaker counts **consecutive** failures: `recordSuccess()` resets the count, and when `recordFailure()` makes the count reach `failureThreshold` the breaker becomes OPEN and remembers the time.
3. **OPEN.** `allowRequest()` returns `false` while less than `openMillis` has passed since the breaker opened. Once `now - openedAt >= openMillis` (the boundary counts), the **next** `allowRequest()` moves the breaker to HALF_OPEN and returns `true`: that call is the trial request.
4. **HALF_OPEN.** While the trial is in flight, `allowRequest()` returns `false`. `recordSuccess()` closes the breaker with a failure count of 0. `recordFailure()` opens it again, and the open time restarts from **now**.
5. **Late results.** `recordSuccess()` and `recordFailure()` are ignored while the breaker is OPEN (they belong to requests sent before it tripped).
6. **state() only reports.** It never changes the state. Time passing does not move an OPEN breaker to HALF_OPEN by itself: only `allowRequest()` does.
7. **Time.** Read time only through `clock`.

### How the sample tests are written

Each test is a script of operations on one breaker, with a fake clock that starts at 0. Every operation prints one observation:

| Operation | Does | Observation |
|---|---|---|
| `new T D` | `new CircuitBreaker(T, D, clock)` | `-` (or `EX:` and the exception name) |
| `at MS` | sets the fake clock to `MS` | `-` |
| `allow` | `allowRequest()` | `true` or `false` |
| `ok` / `fail` | `recordSuccess()` / `recordFailure()` | `-` |
| `state` | `state()` | `CLOSED`, `OPEN` or `HALF_OPEN` |

### What is simplified

Real breakers count failures in a time window or a percentage of calls, track slow calls, and are shared by many threads. This one counts consecutive failures only. It runs in a single JVM and does not make the dependency any more reliable.
',
 '["Model a small state machine with explicit states and transitions.", "Count consecutive failures, and reset the count when a success proves the dependency healthy.", "Use an injected clock for the open duration, including the exact boundary.", "Allow exactly one trial request in HALF_OPEN, and decide what a successful or failed trial does.", "Ignore results that arrive in a state where they no longer matter.", "Keep state() a pure read: only requests move the breaker."]',
 '* `failureThreshold` and `openMillis` are positive for a valid breaker; zero or negative values must be rejected.
* The clock only moves forward in the tests, and time values are small non-negative `long` numbers.
* The tests call the methods one at a time; you may still make the class thread-safe.
* The trial request is whichever `allowRequest()` call first sees the open duration elapsed.',
 '[{"title": "Trips after the threshold", "input": "new 3 1000, fail, fail, state, fail, state", "output": "-, -, -, CLOSED, -, OPEN", "explanation": "Two failures are below the threshold of 3, so the breaker stays CLOSED. The third consecutive failure opens it."}, {"title": "One trial after the cool-down", "input": "new 1 1000, fail, allow, at 999, allow, at 1000, allow, state, allow", "output": "-, -, false, -, false, -, true, HALF_OPEN, false", "explanation": "At time 999 the breaker has been open 999 ms, so it still rejects. At 1000 the boundary is reached: that request is the single trial. Further requests are rejected while the trial is in flight."}, {"title": "A failed trial reopens with a fresh timer", "input": "new 1 100, fail, at 100, allow, fail, at 150, allow, at 200, allow", "output": "-, -, -, true, -, -, false, -, true", "explanation": "The trial fails at time 100, so the breaker opens again and the 100 ms start from 100, not from 0. At 150 only 50 ms have passed; at 200 the trial is allowed."}]',
 'public final class CircuitBreaker {

    public enum State { CLOSED, OPEN, HALF_OPEN }

    public CircuitBreaker(int failureThreshold, long openMillis, Clock clock) {
        // TODO: validate the arguments and keep what you need
    }

    /** True if a request may be sent now. May move OPEN to HALF_OPEN. */
    public boolean allowRequest() {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }

    public void recordSuccess() {
        // TODO
    }

    public void recordFailure() {
        // TODO
    }

    /** Reports the current state. Must not change it. */
    public State state() {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'Trips to OPEN after the threshold', 'state transition', TRUE, '{"script": ["new 3 1000", "fail", "fail", "state", "fail", "state"]}', '{"trace": ["-", "-", "-", "CLOSED", "-", "OPEN"]}'),
 (@version, 1, 'A success resets the consecutive failure count', 'consecutive failures', TRUE, '{"script": ["new 2 500", "fail", "ok", "fail", "state", "fail", "state"]}', '{"trace": ["-", "-", "-", "-", "CLOSED", "-", "OPEN"]}'),
 (@version, 2, 'OPEN rejects, then allows exactly one trial', 'single trial', TRUE, '{"script": ["new 1 1000", "fail", "state", "allow", "at 999", "allow", "at 1000", "allow", "state", "allow"]}', '{"trace": ["-", "-", "OPEN", "false", "-", "false", "-", "true", "HALF_OPEN", "false"]}'),
 (@version, 3, 'The open duration is inclusive', 'boundary', FALSE, '{"script": ["new 1 100", "fail", "at 99", "allow", "state", "at 100", "allow", "state"]}', '{"trace": ["-", "-", "-", "false", "OPEN", "-", "true", "HALF_OPEN"]}'),
 (@version, 4, 'A successful trial closes the breaker and clears the count', 'recovery', FALSE, '{"script": ["new 2 100", "fail", "fail", "at 100", "allow", "ok", "state", "fail", "state", "fail", "state"]}', '{"trace": ["-", "-", "-", "-", "true", "-", "CLOSED", "-", "CLOSED", "-", "OPEN"]}'),
 (@version, 5, 'Reopened after a failed trial: the old timer is gone', 'reopening', FALSE, '{"script": ["new 1 100", "fail", "at 100", "allow", "fail", "state", "at 150", "allow", "at 199", "allow", "at 200", "allow", "state"]}', '{"trace": ["-", "-", "-", "true", "-", "OPEN", "-", "false", "-", "false", "-", "true", "HALF_OPEN"]}'),
 (@version, 6, 'Results that arrive while OPEN are ignored', 'late results', FALSE, '{"script": ["new 2 100", "fail", "fail", "ok", "state", "fail", "state", "at 100", "allow", "state"]}', '{"trace": ["-", "-", "-", "-", "OPEN", "-", "OPEN", "-", "true", "HALF_OPEN"]}'),
 (@version, 7, 'state() reports but never changes the state', 'pure read', FALSE, '{"script": ["new 1 100", "fail", "at 500", "state", "state", "allow", "state"]}', '{"trace": ["-", "-", "-", "OPEN", "OPEN", "true", "HALF_OPEN"]}'),
 (@version, 8, 'A threshold of 1 trips on the first failure', 'threshold', FALSE, '{"script": ["new 1 50", "allow", "fail", "allow", "state"]}', '{"trace": ["-", "true", "-", "false", "OPEN"]}'),
 (@version, 9, 'A zero threshold is rejected', 'validation', FALSE, '{"script": ["new 0 100"]}', '{"trace": ["EX:IllegalArgumentException"]}'),
 (@version, 10, 'A zero open duration is rejected', 'validation', FALSE, '{"script": ["new 3 0"]}', '{"trace": ["EX:IllegalArgumentException"]}'),
 (@version, 11, 'Failures below the threshold keep the breaker CLOSED and allowing', 'threshold', FALSE, '{"script": ["new 5 100", "fail", "fail", "fail", "fail", "state", "allow", "allow"]}', '{"trace": ["-", "-", "-", "-", "-", "CLOSED", "true", "true"]}'),
 (@version, 12, 'A full cycle: closed, open, half-open, closed, open, half-open', 'full cycle', FALSE, '{"script": ["new 2 10", "fail", "fail", "at 10", "allow", "ok", "state", "at 11", "fail", "state", "fail", "state", "at 21", "allow", "state"]}', '{"trace": ["-", "-", "-", "-", "true", "-", "CLOSED", "-", "-", "CLOSED", "-", "OPEN", "-", "true", "HALF_OPEN"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Bulkhead concurrency limiter
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'bulkhead-concurrency-limiter', 'Bulkhead concurrency limiter', 'MEDIUM', 'java17', 'BULKHEAD', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'microservices';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Limit how many operations run at once, reject the rest immediately, and never leak a permit, even when an operation fails.',
 '## Bulkhead concurrency limiter

A **bulkhead** keeps one slow or failing dependency from using up all the capacity of a service. It allows at most a fixed number of calls to run at the same time; any further call is **rejected immediately** rather than made to wait, so the caller can fail fast or fall back, and the other dependencies keep their own capacity.

The SpringLab Microservices Lab shows this in the experiment `res-bulkhead` with real semaphores. Here you write the limiter. This is a single-JVM exercise: it limits threads in one process, not calls across a network or between machines.

### Your task

Write `public final class Bulkhead` with exactly this public API:

```java
public final class Bulkhead {
    public Bulkhead(int maxConcurrent)
    public <T> T execute(Callable<T> operation) throws Exception
    public int inFlight()
    public int availablePermits()
}
```

`BulkheadFullException` is provided. Do not declare it (a source that declares any top-level class other than `Bulkhead` is rejected):

```java
public class BulkheadFullException extends RuntimeException {
    public BulkheadFullException(String message) { super(message); }
}
```

### Required behaviour

1. **Capacity.** If `maxConcurrent < 1`, the constructor throws `IllegalArgumentException`.
2. **Admission.** `execute` runs `operation` and returns its result if fewer than `maxConcurrent` calls are running. Otherwise it throws `BulkheadFullException` **immediately**, without calling `operation` and without waiting.
3. **Release.** The permit is released when `operation` finishes, whether it returned, threw an `Exception` or threw an `Error`. A rejected call never held a permit, so it must not release one.
4. **Exceptions.** If `operation` throws, `execute` lets that same exception propagate.
5. **Accounting.** `inFlight()` is the number of calls currently running and `availablePermits()` the number of free permits. At every moment `inFlight() + availablePermits() == maxConcurrent`.
6. **Threads.** Calls may come from several threads at once. Your class must stay correct then (a `java.util.concurrent.Semaphore` is the natural tool).

### How the sample tests are written

Each test is a script on one bulkhead. A call started with `start` runs on its own thread and **holds its permit until the script says `finish`**, so the script decides exactly how many calls are in flight. Every operation prints one observation:

| Operation | Does | Observation |
|---|---|---|
| `new N` | `new Bulkhead(N)` | `-` (or `EX:` and the exception name) |
| `call ok` / `call fail` | one complete call that returns / throws `IllegalStateException` | `OK`, `FAILED` or `FULL` |
| `start ID ok` / `start ID fail` | starts a call on its own thread that holds its permit | `RUNNING` (it is inside the operation), `FULL` (rejected) or `TIMEOUT` |
| `finish ID` | lets that started call end and waits for it | `OK`, `FAILED` or `FULL` (what that call ended with) |
| `inflight` / `permits` | `inFlight()` / `availablePermits()` | a number |

A call that waits instead of rejecting shows up as `TIMEOUT`.

### What is simplified

Real systems also size bulkheads per dependency, queue a few waiting calls, and expose metrics. This one has no queue and a fixed size. It is not Resilience4j, and it does not isolate failures between separate services or machines.
',
 '["Limit concurrent work to a fixed number of permits.", "Reject immediately when no permit is free instead of waiting.", "Release a permit in every outcome: success, exception, and any error.", "Keep the permit accounting consistent: calls in flight plus free permits equal the capacity.", "Let the operation''s own exception reach the caller unchanged.", "Write code that is correct when several threads call it at the same time."]',
 '* `maxConcurrent` is at least 1 for a valid bulkhead (otherwise `IllegalArgumentException`).
* `operation` may take any time, return any value, or throw any exception or error.
* Several threads may call `execute`, `inFlight` and `availablePermits` at the same time.
* Rejection must be immediate: never block, sleep or retry inside `execute`.',
 '[{"title": "Rejects when full", "input": "new 2, start a ok, start b ok, inflight, start c ok, call ok, inflight", "output": "-, RUNNING, RUNNING, 2, FULL, FULL, 2", "explanation": "Two calls hold the two permits. A third call, started or called directly, is rejected at once and the number in flight stays 2."}, {"title": "A failure releases its permit", "input": "new 1, call fail, permits, call ok", "output": "-, FAILED, 1, OK", "explanation": "The failing operation throws, the exception reaches the caller, and the permit is released, so the next call is admitted."}, {"title": "A finished call frees capacity", "input": "new 1, start a ok, call ok, finish a, call ok, permits", "output": "-, RUNNING, FULL, OK, OK, 1", "explanation": "While call a holds the only permit, another call is rejected. After a finishes, a new call is admitted and the permit comes back."}]',
 'import java.util.concurrent.Callable;

public final class Bulkhead {

    public Bulkhead(int maxConcurrent) {
        // TODO: validate and set up your permits
    }

    /** Runs the operation if a permit is free; otherwise throws BulkheadFullException at once. */
    public <T> T execute(Callable<T> operation) throws Exception {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** The number of calls running right now. */
    public int inFlight() {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** The number of free permits. */
    public int availablePermits() {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A call below capacity runs and releases its permit', 'basic flow', TRUE, '{"script": ["new 2", "call ok", "inflight", "permits"]}', '{"trace": ["-", "OK", "0", "2"]}'),
 (@version, 1, 'Rejects immediately when every permit is taken', 'rejection', TRUE, '{"script": ["new 2", "start a ok", "start b ok", "inflight", "start c ok", "call ok", "inflight"]}', '{"trace": ["-", "RUNNING", "RUNNING", "2", "FULL", "FULL", "2"]}'),
 (@version, 2, 'A finished call frees a permit', 'release', TRUE, '{"script": ["new 1", "start a ok", "call ok", "finish a", "call ok", "permits"]}', '{"trace": ["-", "RUNNING", "FULL", "OK", "OK", "1"]}'),
 (@version, 3, 'A failing operation releases its permit', 'release on failure', FALSE, '{"script": ["new 1", "call fail", "permits", "call ok"]}', '{"trace": ["-", "FAILED", "1", "OK"]}'),
 (@version, 4, 'The operation''s own failure reaches the caller', 'exception propagation', FALSE, '{"script": ["new 1", "start a fail", "finish a", "inflight"]}', '{"trace": ["-", "RUNNING", "FAILED", "0"]}'),
 (@version, 5, 'A rejected call does not disturb the calls in flight', 'rejection', FALSE, '{"script": ["new 1", "start a ok", "start b ok", "finish b", "inflight", "finish a", "inflight"]}', '{"trace": ["-", "RUNNING", "FULL", "FULL", "1", "OK", "0"]}'),
 (@version, 6, 'Capacity of three with one failing call', 'capacity', FALSE, '{"script": ["new 3", "start a ok", "start b fail", "start c ok", "start d ok", "permits", "finish b", "permits", "start e ok", "inflight"]}', '{"trace": ["-", "RUNNING", "RUNNING", "RUNNING", "FULL", "0", "FAILED", "1", "RUNNING", "3"]}'),
 (@version, 7, 'No permit leaks after many mixed calls', 'permit leak', FALSE, '{"script": ["new 2", "call fail", "call ok", "call fail", "call ok", "call fail", "permits", "inflight"]}', '{"trace": ["-", "FAILED", "OK", "FAILED", "OK", "FAILED", "2", "0"]}'),
 (@version, 8, 'A capacity of zero is rejected', 'validation', FALSE, '{"script": ["new 0"]}', '{"trace": ["EX:IllegalArgumentException"]}'),
 (@version, 9, 'Permit accounting stays consistent through a sequence', 'accounting', FALSE, '{"script": ["new 3", "start a ok", "inflight", "permits", "start b ok", "inflight", "permits", "finish a", "inflight", "permits"]}', '{"trace": ["-", "RUNNING", "1", "2", "RUNNING", "2", "1", "OK", "1", "2"]}'),
 (@version, 10, 'Permits can be released in any order', 'release order', FALSE, '{"script": ["new 2", "start a ok", "start b ok", "finish a", "start c ok", "start d ok", "finish b", "finish c", "inflight"]}', '{"trace": ["-", "RUNNING", "RUNNING", "OK", "RUNNING", "FULL", "OK", "OK", "0"]}'),
 (@version, 11, 'Rejection returns at once', 'fail fast', FALSE, '{"script": ["new 1", "start a ok", "start b ok", "start c ok", "finish a", "permits"]}', '{"trace": ["-", "RUNNING", "FULL", "FULL", "OK", "1"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Idempotency key handler
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'idempotency-key-handler', 'Idempotency key handler', 'MEDIUM', 'java17', 'IDEMPOTENCY', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'microservices';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Make a retried request safe: run an operation once per key, return the stored result for duplicates, and refuse a duplicate that is still running.',
 '## Idempotency key handler

Networks lose replies, so clients retry. If a payment request is sent twice, the customer must be charged once. An **idempotency key** identifies one logical request: the first request with a key runs, and every repeat gets the **original result** instead of running again.

The SpringLab Microservices Lab shows this in the experiment `res-idempotency-key` and in the idempotent consumer of `comm-async-duplicate`. This exercise is plain Java in one JVM with a fake clock; it keeps keys in memory and does not use a database.

### Your task

Write `public final class IdempotencyHandler` with exactly this public API:

```java
public final class IdempotencyHandler {
    public IdempotencyHandler(long ttlMillis, Clock clock)
    public <T> T handle(String key, Callable<T> operation) throws Exception
}
```

`Clock` and `RequestInProgressException` are provided. Do not declare them (a source that declares any top-level class other than `IdempotencyHandler` is rejected):

```java
public interface Clock { long nowMillis(); }
public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException(String message) { super(message); }
}
```

### Required behaviour

1. **Arguments.** `ttlMillis < 1` makes the constructor throw `IllegalArgumentException`. A `null` or empty `key` makes `handle` throw `IllegalArgumentException`.
2. **Scope.** A key is scoped to one handler object and compared exactly (case-sensitive). Two handlers do not share keys. (In a real service the scope is usually a user or a client plus an endpoint; here it is the handler.)
3. **First request.** With a new key, `handle` runs `operation`, stores its result under the key, and returns it.
4. **Duplicate after completion.** While the stored result is younger than `ttlMillis`, `handle` with the same key returns the **same stored result** and does **not** call `operation`. The age is `clock.nowMillis() - completedAt`, where `completedAt` is the time the first operation finished.
5. **Lifetime.** When the age is `ttlMillis` or more, the key is forgotten and the request runs again as a new one (the boundary counts as expired).
6. **Duplicate while running.** If a request with the same key is still running, a second `handle` call must **not** run the operation. It throws `RequestInProgressException`. A different key is independent.
7. **Failure.** If `operation` throws, nothing is stored and the key is **released**: the exception propagates unchanged, and a retry with the same key runs the operation again.
8. **Time.** Read time only through `clock`.

### How the sample tests are written

Each test is a script on one handler, with a fake clock that starts at 0. A duplicate that arrives while the first request is running is simulated deterministically: the running operation itself calls the handler again (`dup:KEY`). Every operation prints one observation:

| Operation | Does | Observation |
|---|---|---|
| `new TTL` | `new IdempotencyHandler(TTL, clock)` | `-` (or `EX:` and the exception name) |
| `at MS` | sets the fake clock | `-` |
| `handle KEY ret:V` | `handle(KEY, op)`; the operation returns `V` | `V#new` if the operation ran in this call, `V#cached` if a stored result was returned |
| `handle KEY fail` | the operation throws `IllegalStateException` | `FAILED` (that exception reached the caller) |
| `handle KEY dup:K2` | while running, the operation calls `handle(K2, ...)` and returns `dup=` plus what that call observed | like `ret`, with the nested observation inside the value |
| `runs` | how many times any operation has run | a number |

`KEY` is a word, or `empty` for the empty string. An exception from your code prints as `EX:` and its name, for example `EX:RequestInProgressException`.

### What is simplified

A real idempotency store is shared by many servers, must survive restarts, and usually stores a fingerprint of the request body so a reused key with a different body is refused. This one is in memory in one JVM, has no persistence, and trusts the key alone. It does not make the operation''s side effects safe on its own: it only avoids running it twice.
',
 '["Detect a duplicate request by its idempotency key.", "Return the original result for a repeated request without running the operation again.", "Tell a request that is still in progress apart from one that has completed.", "Decide what a failure leaves behind: here, nothing, so the request can be retried.", "Define the scope and the lifetime of a key, and enforce the lifetime with an injected clock.", "Keep the bookkeeping consistent when the operation throws."]',
 '* `ttlMillis` is at least 1 for a valid handler.
* Keys are non-empty strings; the tests use short words.
* The operation may return any value (the tests use short strings) or throw.
* A duplicate that arrives while the first request runs is a call to `handle` from inside the running operation.',
 '[{"title": "A repeat returns the original result", "input": "new 1000, handle pay ret:ok, handle pay ret:other, runs", "output": "-, ok#new, ok#cached, 1", "explanation": "The second request has the same key, so it gets the stored result `ok`, not `other`, and the operation does not run again."}, {"title": "A failure can be retried", "input": "new 1000, handle k fail, handle k ret:done, handle k ret:again, runs", "output": "-, FAILED, done#new, done#cached, 2", "explanation": "The failed request left nothing behind, so the retry runs and succeeds. The next repeat then gets the stored `done`."}, {"title": "A duplicate while running is refused", "input": "new 1000, handle k dup:k, runs", "output": "-, dup=EX:RequestInProgressException#new, 1", "explanation": "Inside the running operation the same key is used again. That nested call is refused with RequestInProgressException and the operation ran only once."}]',
 'import java.util.concurrent.Callable;

public final class IdempotencyHandler {

    public IdempotencyHandler(long ttlMillis, Clock clock) {
        // TODO: validate and keep what you need
    }

    /**
     * Runs the operation once per key, returns the stored result for a repeat, and refuses a repeat that is still running.
     * See the problem statement for the exact rules.
     */
    public <T> T handle(String key, Callable<T> operation) throws Exception {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'A repeated request returns the original result without running again', 'duplicate detection', TRUE, '{"script": ["new 1000", "handle pay ret:ok", "handle pay ret:other", "runs"]}', '{"trace": ["-", "ok#new", "ok#cached", "1"]}'),
 (@version, 1, 'Different keys are independent', 'key scope', TRUE, '{"script": ["new 1000", "handle a ret:one", "handle b ret:two", "handle a ret:x", "runs"]}', '{"trace": ["-", "one#new", "two#new", "one#cached", "2"]}'),
 (@version, 2, 'A failure is not remembered, so the request can be retried', 'failure and retry', TRUE, '{"script": ["new 1000", "handle k fail", "handle k ret:done", "handle k ret:again", "runs"]}', '{"trace": ["-", "FAILED", "done#new", "done#cached", "2"]}'),
 (@version, 3, 'A duplicate that arrives while the first is running is refused', 'in progress', FALSE, '{"script": ["new 1000", "handle k dup:k", "handle k ret:x", "runs"]}', '{"trace": ["-", "dup=EX:RequestInProgressException#new", "dup=EX:RequestInProgressException#cached", "1"]}'),
 (@version, 4, 'A different key may run while another is in progress', 'in progress', FALSE, '{"script": ["new 1000", "handle a dup:b", "runs"]}', '{"trace": ["-", "dup=inner#new#new", "2"]}'),
 (@version, 5, 'A stored result expires after the lifetime', 'lifetime', FALSE, '{"script": ["new 100", "handle k ret:first", "at 99", "handle k ret:second", "at 100", "handle k ret:third", "runs"]}', '{"trace": ["-", "first#new", "-", "first#cached", "-", "third#new", "2"]}'),
 (@version, 6, 'The lifetime counts from completion and restarts after an expiry', 'lifetime', FALSE, '{"script": ["new 100", "at 50", "handle k ret:a", "at 149", "handle k ret:b", "at 150", "handle k ret:c", "at 249", "handle k ret:d"]}', '{"trace": ["-", "-", "a#new", "-", "a#cached", "-", "c#new", "-", "c#cached"]}'),
 (@version, 7, 'The empty key is rejected', 'validation', FALSE, '{"script": ["new 100", "handle empty ret:x", "runs"]}', '{"trace": ["-", "EX:IllegalArgumentException", "0"]}'),
 (@version, 8, 'A zero lifetime is rejected', 'validation', FALSE, '{"script": ["new 0"]}', '{"trace": ["EX:IllegalArgumentException"]}'),
 (@version, 9, 'After a failure the key is not left in progress', 'failure and retry', FALSE, '{"script": ["new 100", "handle k fail", "handle k dup:k", "runs"]}', '{"trace": ["-", "FAILED", "dup=EX:RequestInProgressException#new", "2"]}'),
 (@version, 10, 'A completed key is not in progress: a nested repeat gets the stored result', 'completed versus in progress', FALSE, '{"script": ["new 100", "handle k ret:x", "handle other dup:k", "runs"]}', '{"trace": ["-", "x#new", "dup=x#cached#new", "2"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Saga compensation coordinator
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'saga-compensation-coordinator', 'Saga compensation coordinator', 'HARD', 'java17', 'SAGA', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'microservices';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Run steps in order; when one fails, undo the completed steps in reverse order, keep going if an undo fails, and report an explicit final state.',
 '## Saga compensation coordinator

When a business operation spans several services, there is no single database transaction to roll back. A **saga** runs the steps one after another, each in its own local transaction. If a step fails, the saga undoes the steps that already completed by running their **compensations** in **reverse order**. A compensation is a new operation (release the stock, refund the payment), not a rollback, and it can fail too.

The SpringLab Microservices Lab shows an orchestrated saga in the experiment `saga-orchestrated`, including a compensation that fails. This exercise is the coordinator, in plain Java in one JVM. Nothing here is a distributed transaction or a database.

### Your task

Write `public final class SagaCoordinator` with exactly this public API:

```java
public final class SagaCoordinator {
    public SagaCoordinator(List<SagaStep> steps)
    public SagaStatus run()
    public List<String> pendingCompensations()
}
```

`SagaStep` and `SagaStatus` are provided. Do not declare them (a source that declares any top-level class other than `SagaCoordinator` is rejected):

```java
public interface SagaStep {
    String name();
    void execute() throws Exception;
    void compensate() throws Exception;
}

public enum SagaStatus { COMPLETED, COMPENSATED, COMPENSATION_FAILED }
```

### Required behaviour

1. **Forward.** `run()` calls `execute()` on the steps in list order. It stops at the first step whose `execute()` throws: no later step is executed.
2. **All succeed.** If every step succeeds, no compensation is called and `run()` returns `COMPLETED`.
3. **Compensate in reverse.** When step *i* fails, call `compensate()` on the steps that **completed** before it, from the most recent to the first. The failed step is **not** compensated (it did not complete), and steps after it were never executed.
4. **A compensation can fail.** If a `compensate()` throws, keep going with the remaining earlier steps. Each failed compensation is **not retried**; its step name is added to the pending list.
5. **Final state.** After compensating, return `COMPENSATED` if every compensation succeeded (this includes the case where nothing had completed), or `COMPENSATION_FAILED` if at least one failed.
6. **Recovery.** `pendingCompensations()` returns the names of the steps whose compensation failed, in the order the compensations were attempted. Those steps need manual recovery; the coordinator does not retry them. The list is empty when nothing failed.
7. **Run once.** A second call to `run()` throws `IllegalStateException`, whatever the first run returned.
8. **Empty saga.** With no steps, `run()` returns `COMPLETED`.

### How the sample tests are written

Each test builds one coordinator from scripted steps and records every call made on them. A step is written `NAME=EXEC/COMP`, where `EXEC` and `COMP` are `ok` or `fail`, meaning whether its `execute()` and its `compensate()` succeed. Every operation prints one observation:

| Operation | Does | Observation |
|---|---|---|
| `new STEP STEP ...` | builds the steps and `new SagaCoordinator(steps)`; `-` means no steps | `-` |
| `run` | `run()` | the status name, or `EX:` and the exception name |
| `calls` | every call made on the steps so far | for example `execute:A,execute:B,compensate:A`, or `-` |
| `pending` | `pendingCompensations()` | the names joined by commas, or `-` |

### What is simplified

A production saga persists its progress so it can resume after a crash, retries compensations with backoff, runs steps in different services, and relies on idempotent steps. This coordinator keeps everything in memory in one run and reports failed compensations instead of retrying them. It does not provide atomicity: while the saga runs, other readers can see the intermediate states.
',
 '["Run a sequence of steps in order and stop at the first failure.", "Compensate only the steps that completed, in reverse order.", "Understand that a compensation is a new operation that can itself fail.", "Keep compensating after a compensation fails, and record exactly which ones need manual recovery.", "Report an explicit final state: COMPLETED, COMPENSATED or COMPENSATION_FAILED.", "Make a coordinator run once, so a finished saga cannot be started again by accident."]',
 '* A saga has between 0 and about 20 steps; the tests use at most 5.
* Step names are short words and are unique within a saga.
* `execute()` and `compensate()` may throw any `Exception`; compensation of one step must not prevent the others.
* `run()` is called at most twice per coordinator in the tests.',
 '[{"title": "A failure compensates in reverse", "input": "new A=ok/ok B=ok/ok C=fail/ok, run, calls, pending", "output": "-, COMPENSATED, execute:A,execute:B,execute:C,compensate:B,compensate:A, -", "explanation": "C failed, so B and A (completed) are compensated, most recent first. C itself is not compensated."}, {"title": "A compensation that fails is reported, not retried", "input": "new A=ok/ok B=ok/fail C=ok/ok D=fail/ok, run, calls, pending", "output": "-, COMPENSATION_FAILED, execute:A,execute:B,execute:C,execute:D,compensate:C,compensate:B,compensate:A, B", "explanation": "The compensation of B failed, but the coordinator still compensated A. The final state is COMPENSATION_FAILED and B is listed for manual recovery."}, {"title": "A first-step failure compensates nothing", "input": "new A=fail/ok B=ok/ok, run, calls", "output": "-, COMPENSATED, execute:A", "explanation": "Nothing had completed, so there is nothing to undo. B never ran."}]',
 'import java.util.List;

public final class SagaCoordinator {

    public SagaCoordinator(List<SagaStep> steps) {
        // TODO: keep a copy of the steps
    }

    /** Executes the steps in order; on a failure compensates the completed ones in reverse order. */
    public SagaStatus run() {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** Names of the steps whose compensation failed, in the order they were attempted. */
    public List<String> pendingCompensations() {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'All steps succeed', 'forward execution', TRUE, '{"script": ["new A=ok/ok B=ok/ok C=ok/ok", "run", "calls", "pending"]}', '{"trace": ["-", "COMPLETED", "execute:A,execute:B,execute:C", "-"]}'),
 (@version, 1, 'A failure compensates the earlier steps in reverse order', 'reverse compensation', TRUE, '{"script": ["new A=ok/ok B=ok/ok C=fail/ok", "run", "calls", "pending"]}', '{"trace": ["-", "COMPENSATED", "execute:A,execute:B,execute:C,compensate:B,compensate:A", "-"]}'),
 (@version, 2, 'A failure in the first step compensates nothing', 'nothing to undo', TRUE, '{"script": ["new A=fail/ok B=ok/ok", "run", "calls"]}', '{"trace": ["-", "COMPENSATED", "execute:A"]}'),
 (@version, 3, 'The failed step itself is not compensated', 'failed step', FALSE, '{"script": ["new A=ok/ok B=fail/fail", "run", "calls", "pending"]}', '{"trace": ["-", "COMPENSATED", "execute:A,execute:B,compensate:A", "-"]}'),
 (@version, 4, 'No step runs after the failing one', 'stop at first failure', FALSE, '{"script": ["new A=ok/ok B=fail/ok C=ok/ok D=ok/ok", "run", "calls"]}', '{"trace": ["-", "COMPENSATED", "execute:A,execute:B,compensate:A"]}'),
 (@version, 5, 'A failing compensation does not stop the others', 'compensation failure', FALSE, '{"script": ["new A=ok/ok B=ok/fail C=ok/ok D=fail/ok", "run", "calls", "pending"]}', '{"trace": ["-", "COMPENSATION_FAILED", "execute:A,execute:B,execute:C,execute:D,compensate:C,compensate:B,compensate:A", "B"]}'),
 (@version, 6, 'Several compensation failures are all reported in the order attempted', 'pending list', FALSE, '{"script": ["new A=ok/fail B=ok/fail C=ok/ok D=fail/ok", "run", "pending"]}', '{"trace": ["-", "COMPENSATION_FAILED", "B,A"]}'),
 (@version, 7, 'An empty saga completes', 'empty saga', FALSE, '{"script": ["new -", "run", "calls"]}', '{"trace": ["-", "COMPLETED", "-"]}'),
 (@version, 8, 'A saga runs once', 'run once', FALSE, '{"script": ["new A=ok/ok", "run", "run", "calls"]}', '{"trace": ["-", "COMPLETED", "EX:IllegalStateException", "execute:A"]}'),
 (@version, 9, 'A second run is refused after a failure and the pending list is kept', 'run once', FALSE, '{"script": ["new A=ok/fail B=fail/ok", "run", "run", "pending"]}', '{"trace": ["-", "COMPENSATION_FAILED", "EX:IllegalStateException", "A"]}'),
 (@version, 10, 'A long saga failing at the last step compensates everything before it', 'reverse compensation', FALSE, '{"script": ["new A=ok/ok B=ok/ok C=ok/ok D=ok/ok E=fail/ok", "run", "calls"]}', '{"trace": ["-", "COMPENSATED", "execute:A,execute:B,execute:C,execute:D,execute:E,compensate:D,compensate:C,compensate:B,compensate:A"]}');

-- ---------------------------------------------------------------------------------------------------------------------------------
-- Transactional outbox relay
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'transactional-outbox-relay', 'Transactional outbox relay', 'HARD', 'java17', 'OUTBOX', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'microservices';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Relay outbox events in order with at-least-once delivery, handle publish and database failures, and write a consumer that ignores duplicates.',
 '## Transactional outbox relay

A service that changes its database and must also tell other services needs both to happen reliably. The **transactional outbox** writes the event to an `outbox` table in the same database transaction as the change. A separate **relay** then reads the unpublished rows and publishes them. Because publishing and recording the result are two steps, a crash between them means an event can be delivered **more than once**: delivery is **at-least-once**, so consumers must be idempotent.

The SpringLab Microservices Lab shows this in the experiments `outbox-dual-write-gap`, `outbox-commit-rollback` and `outbox-duplicate-delivery`. Here you write the relay and the consumer. The store, the publisher and their failures are fakes in one JVM: there is no real database, no broker, and no Kafka or RabbitMQ.

### Your task

Write `public final class OutboxRelay` with exactly this public API, including the nested consumer class:

```java
public final class OutboxRelay {
    public OutboxRelay(OutboxStore store, EventPublisher publisher)
    public int relayBatch(int maxEvents)

    public static final class IdempotentConsumer {
        public IdempotentConsumer()
        public boolean consume(OutboxEvent event)
        public List<Long> applied()
    }
}
```

These types are provided. Do not declare them (a source that declares any top-level class other than `OutboxRelay` is rejected):

```java
public record OutboxEvent(long id, String payload) { }

public interface OutboxStore {
    List<OutboxEvent> fetchUnpublished(int limit);   // oldest (lowest id) first
    void markPublished(long id);                     // may throw
}

public interface EventPublisher {
    void publish(OutboxEvent event) throws Exception; // may throw
}
```

### Required behaviour

1. **Batch size.** If `maxEvents < 1`, `relayBatch` throws `IllegalArgumentException`. Otherwise it asks the store for at most `maxEvents` unpublished events.
2. **Order.** Process the events in the order the store returned them.
3. **Publish, then record.** For each event call `publisher.publish(event)` first, and only if that succeeds call `store.markPublished(event.id())`. Never mark an event before it has been published.
4. **Stop at a failure.** If `publish` throws, stop the batch immediately: the failed event stays unpublished and **no later event is sent**, so order is kept. If `markPublished` throws after a successful publish, also stop: the event was delivered but not recorded, so the next batch will send it **again** (this is the duplicate).
5. **Result.** `relayBatch` never throws because of a publisher or store failure. It returns how many events were published **and** marked in this call.
6. **The consumer.** `IdempotentConsumer.consume(event)` applies an event the first time its id is seen and returns `true`; a repeat of an id returns `false` and changes nothing. `applied()` returns the ids applied, in the order they were first applied, as a new list.

### How the sample tests are written

Each test script uses a fake outbox table and a fake publisher with scripted failures. Every event the publisher accepts is also handed to your `IdempotentConsumer`, as a subscriber would receive it. Every operation prints one observation:

| Operation | Does | Observation |
|---|---|---|
| `events 1,2,3` | adds rows with those ids (oldest first) | `-` |
| `failpub ID N` | the next N `publish` calls for that event throw | `-` |
| `failmark ID N` | the next N `markPublished` calls for that event throw | `-` |
| `relay MAX` | `relayBatch(MAX)` | the number it returned, or `EX:` and the exception name |
| `consume ID` | hands one event straight to your consumer | `true` or `false` |
| `delivered` | every accepted publish, in order, duplicates included | ids joined by commas, or `-` |
| `applied` | your consumer''s `applied()` | ids joined by commas, or `-` |
| `unpublished` | rows not yet marked published | ids joined by commas, or `-` |

### What is simplified

A real relay polls on a schedule, locks rows so several relays do not collide, batches in a transaction, and a consumer stores processed ids in its own database. Here everything is in memory. The consumer shows the idea (record the id with the effect) without a transaction. This is not exactly-once delivery: the guarantee is at-least-once delivery combined with idempotent consumption.
',
 '["Publish events from an outbox table in the order they were written.", "Mark an event published only after it has been delivered, never before.", "Stop a batch at the first failure so later events cannot overtake an earlier one.", "See why a failure between delivery and recording causes a duplicate: at-least-once delivery.", "Write an idempotent consumer that applies each event once, by its id.", "Respect a batch size limit and reject an invalid one."]',
 '* `maxEvents` is at least 1 for a valid batch.
* Event ids are positive `long` values and increase in the order the events were written.
* `publish` and `markPublished` may throw at any time; both are retried only by a later `relayBatch` call.
* The store returns events oldest first; keep that order.',
 '[{"title": "A publish failure stops the batch", "input": "events 1,2,3, failpub 2 1, relay 10, delivered, unpublished", "output": "-, -, 1, 1, \\"2,3\\"", "explanation": "Event 1 was published and marked. Publishing event 2 failed, so the batch stopped: event 3 was not sent ahead of event 2."}, {"title": "A recording failure causes a duplicate", "input": "events 1,2, failmark 1 1, relay 10, relay 10, delivered, applied", "output": "-, -, 0, 2, \\"1,1,2\\", \\"1,2\\"", "explanation": "Event 1 was delivered but marking it failed, so the first batch ended with 0. The second batch delivered it again (the duplicate) and then event 2. The idempotent consumer applied event 1 only once."}, {"title": "The consumer ignores a repeat", "input": "consume 7, consume 7, consume 8, applied", "output": "true, false, true, \\"7,8\\"", "explanation": "The second delivery of id 7 changes nothing."}]',
 'import java.util.List;

public final class OutboxRelay {

    public OutboxRelay(OutboxStore store, EventPublisher publisher) {
        // TODO: keep the store and the publisher
    }

    /** Publishes up to maxEvents unpublished events in order and returns how many were published and marked. */
    public int relayBatch(int maxEvents) {
        // TODO
        throw new UnsupportedOperationException("not implemented yet");
    }

    /** Applies each event once, by id. */
    public static final class IdempotentConsumer {

        public IdempotentConsumer() {
        }

        /** True the first time an id is seen, false for a repeat. */
        public boolean consume(OutboxEvent event) {
            // TODO
            throw new UnsupportedOperationException("not implemented yet");
        }

        /** The ids applied so far, in the order they were first applied. */
        public List<Long> applied() {
            // TODO
            throw new UnsupportedOperationException("not implemented yet");
        }
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'Events are relayed in order and marked published', 'ordered relay', TRUE, '{"script": ["events 1,2,3", "relay 10", "delivered", "unpublished"]}', '{"trace": ["-", "3", "1,2,3", "-"]}'),
 (@version, 1, 'The batch limit is respected', 'batch size', TRUE, '{"script": ["events 1,2,3,4", "relay 2", "delivered", "unpublished", "relay 2", "delivered"]}', '{"trace": ["-", "2", "1,2", "3,4", "2", "1,2,3,4"]}'),
 (@version, 2, 'A publish failure stops the batch and keeps the order', 'publish failure', TRUE, '{"script": ["events 1,2,3", "failpub 2 1", "relay 10", "delivered", "unpublished"]}', '{"trace": ["-", "-", "1", "1", "2,3"]}'),
 (@version, 3, 'The failed event is retried first on the next batch', 'retry', FALSE, '{"script": ["events 1,2,3", "failpub 2 1", "relay 10", "relay 10", "delivered", "unpublished"]}', '{"trace": ["-", "-", "1", "2", "1,2,3", "-"]}'),
 (@version, 4, 'A failure to record a delivery causes a second delivery (at least once)', 'at-least-once', FALSE, '{"script": ["events 1,2", "failmark 1 1", "relay 10", "delivered", "unpublished", "relay 10", "delivered"]}', '{"trace": ["-", "-", "0", "1", "1,2", "2", "1,1,2"]}'),
 (@version, 5, 'The idempotent consumer applies a duplicate delivery once', 'idempotent consumption', FALSE, '{"script": ["events 1,2", "failmark 1 1", "relay 10", "relay 10", "delivered", "applied"]}', '{"trace": ["-", "-", "0", "2", "1,1,2", "1,2"]}'),
 (@version, 6, 'Repeated recording failures mean repeated deliveries', 'at-least-once', FALSE, '{"script": ["events 1", "failmark 1 2", "relay 5", "relay 5", "relay 5", "delivered", "applied", "unpublished"]}', '{"trace": ["-", "-", "0", "0", "1", "1,1,1", "1", "-"]}'),
 (@version, 7, 'Nothing to relay', 'empty outbox', FALSE, '{"script": ["relay 5", "delivered"]}', '{"trace": ["0", "-"]}'),
 (@version, 8, 'A batch size below one is rejected', 'validation', FALSE, '{"script": ["events 1", "relay 0", "relay -1"]}', '{"trace": ["-", "EX:IllegalArgumentException", "EX:IllegalArgumentException"]}'),
 (@version, 9, 'The consumer ignores duplicates by itself', 'idempotent consumption', FALSE, '{"script": ["consume 7", "consume 7", "consume 8", "consume 7", "applied"]}', '{"trace": ["true", "false", "true", "false", "7,8"]}'),
 (@version, 10, 'Published events are never sent again', 'no resend', FALSE, '{"script": ["events 1,2", "relay 10", "relay 10", "delivered"]}', '{"trace": ["-", "2", "0", "1,2"]}'),
 (@version, 11, 'A failure of the first event sends nothing', 'ordering', FALSE, '{"script": ["events 1,2", "failpub 1 1", "relay 10", "delivered", "unpublished"]}', '{"trace": ["-", "-", "0", "-", "1,2"]}'),
 (@version, 12, 'A recording failure in the middle keeps the later events behind it', 'ordering', FALSE, '{"script": ["events 1,2,3", "failmark 2 1", "relay 10", "delivered", "unpublished"]}', '{"trace": ["-", "-", "1", "1,2", "2,3"]}');
