-- Coding practice (Phase F3): problems, immutable problem versions with their tests, submissions, per-test results and progress.
-- Coding practice is separate from the Challenge Arena: no table here is shared with questions, attempts or answers.
--
-- Versioning: a problem row names its CURRENT version. A version (statement, starter code, tests) is never edited after it exists; changing a
-- problem means adding a new version with its own test rows and moving current_version. A submission points at the exact version it was
-- judged against, so its verdict, its counts and its per-test results stay interpretable however the problem changes later.
--
-- Visible and hidden tests live in the same table (coding_test_cases.visible). Hidden inputs and expected values are read only by the
-- backend, which hands them to the trusted worker driver; no learner-facing response is built from them.

CREATE TABLE coding_problems (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    topic_id        BIGINT       NOT NULL,
    slug            VARCHAR(80)  NOT NULL,
    title           VARCHAR(200) NOT NULL,
    difficulty      VARCHAR(10)  NOT NULL,
    language        VARCHAR(20)  NOT NULL,
    harness_kind    VARCHAR(30)  NOT NULL, -- which fixed worker harness judges this problem; never taken from a request
    current_version INT          NOT NULL,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coding_problems_slug UNIQUE (slug),
    CONSTRAINT fk_coding_problems_topic FOREIGN KEY (topic_id) REFERENCES topics (id),
    CONSTRAINT ck_coding_problems_difficulty CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_coding_problems_harness CHECK (harness_kind IN ('RETRY_EXECUTOR')),
    INDEX ix_coding_problems_topic (topic_id, active)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE coding_problem_versions (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    problem_id      BIGINT       NOT NULL,
    version         INT          NOT NULL,
    summary         VARCHAR(500) NOT NULL,
    statement_md    TEXT         NOT NULL,
    objectives_json TEXT         NOT NULL,
    constraints_md  TEXT         NOT NULL,
    examples_json   TEXT         NOT NULL,
    starter_code    TEXT         NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_coding_versions_problem FOREIGN KEY (problem_id) REFERENCES coding_problems (id),
    CONSTRAINT uk_coding_versions UNIQUE (problem_id, version)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE coding_test_cases (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    problem_version_id BIGINT       NOT NULL,
    sort_order         INT          NOT NULL,
    name               VARCHAR(200) NOT NULL,
    concept            VARCHAR(100) NOT NULL,
    visible            BOOLEAN      NOT NULL,
    input_spec         TEXT         NOT NULL, -- JSON: the scripted scenario sent into the sandbox
    expected_spec      TEXT         NOT NULL, -- JSON: the expected observation; stays in the trusted driver, never enters the sandbox
    PRIMARY KEY (id),
    CONSTRAINT fk_coding_tests_version FOREIGN KEY (problem_version_id) REFERENCES coding_problem_versions (id),
    CONSTRAINT uk_coding_tests_order UNIQUE (problem_version_id, sort_order),
    INDEX ix_coding_tests_visible (problem_version_id, visible)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Only FULL submissions are stored (sample runs are not). A row is written when judging has finished.
CREATE TABLE coding_submissions (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    user_id            BIGINT      NOT NULL,
    problem_id         BIGINT      NOT NULL,
    problem_version_id BIGINT      NOT NULL,
    source_code        MEDIUMTEXT  NOT NULL,
    source_sha256      CHAR(64)    NOT NULL,
    verdict            VARCHAR(20) NOT NULL,
    passed             INT         NOT NULL,
    total              INT         NOT NULL,
    compiler_output    TEXT        NULL,
    judge_millis       INT         NULL,      -- how long judging took, for operations; not shown as the learner's runtime
    created_at         DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_coding_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coding_submissions_problem FOREIGN KEY (problem_id) REFERENCES coding_problems (id),
    CONSTRAINT fk_coding_submissions_version FOREIGN KEY (problem_version_id) REFERENCES coding_problem_versions (id),
    CONSTRAINT ck_coding_submissions_verdict CHECK (verdict IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILE_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT',
                                                                'MEMORY_LIMIT', 'OUTPUT_LIMIT', 'INTERNAL_ERROR', 'CANCELLED')),
    INDEX ix_coding_submissions_user_problem (user_id, problem_id, created_at),
    INDEX ix_coding_submissions_version (problem_version_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE coding_submission_results (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    submission_id BIGINT      NOT NULL,
    test_case_id  BIGINT      NOT NULL,
    status        VARCHAR(10) NOT NULL,
    message       VARCHAR(500) NULL,          -- only ever filled for a VISIBLE test
    PRIMARY KEY (id),
    CONSTRAINT fk_coding_results_submission FOREIGN KEY (submission_id) REFERENCES coding_submissions (id) ON DELETE CASCADE,
    CONSTRAINT fk_coding_results_test FOREIGN KEY (test_case_id) REFERENCES coding_test_cases (id),
    CONSTRAINT uk_coding_results UNIQUE (submission_id, test_case_id),
    CONSTRAINT ck_coding_results_status CHECK (status IN ('PASS', 'FAIL', 'NOT_RUN', 'ERROR', 'TIMEOUT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- One row per (user, problem). attempts counts judged full submissions (a result caused by the platform, INTERNAL_ERROR or CANCELLED,
-- is stored in coding_submissions but is not an attempt). accepted_at and first_accepted_submission_id are set once, by the first ACCEPTED.
CREATE TABLE coding_progress (
    id                          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                     BIGINT      NOT NULL,
    problem_id                  BIGINT      NOT NULL,
    attempts                    INT         NOT NULL,
    accepted_submissions        INT         NOT NULL,
    first_accepted_submission_id BIGINT     NULL,
    accepted_at                 DATETIME(6) NULL,
    latest_submission_id        BIGINT      NOT NULL,
    latest_verdict              VARCHAR(20) NOT NULL,
    latest_at                   DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_coding_progress UNIQUE (user_id, problem_id),
    CONSTRAINT fk_coding_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_coding_progress_problem FOREIGN KEY (problem_id) REFERENCES coding_problems (id),
    CONSTRAINT fk_coding_progress_first FOREIGN KEY (first_accepted_submission_id) REFERENCES coding_submissions (id),
    CONSTRAINT fk_coding_progress_latest FOREIGN KEY (latest_submission_id) REFERENCES coding_submissions (id),
    INDEX ix_coding_progress_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ---------------------------------------------------------------------------------------------------------------------------------
-- The pilot problem. Its expected observations were computed from a model of the exercise (see docs/CODING_PLATFORM.md).
INSERT INTO coding_problems (topic_id, slug, title, difficulty, language, harness_kind, current_version, active, created_at, updated_at)
SELECT id, 'retry-with-bounded-backoff', 'Retry with bounded backoff', 'MEDIUM', 'java17', 'RETRY_EXECUTOR', 1, TRUE, NOW(6), NOW(6) FROM topics WHERE slug = 'microservices';
SET @problem = LAST_INSERT_ID();

INSERT INTO coding_problem_versions (problem_id, version, summary, statement_md, objectives_json, constraints_md, examples_json, starter_code, created_at)
VALUES (@problem, 1, 'Implement a retry executor that waits between attempts, doubles the wait each time, and never waits longer than a cap.',
 '## Retry with bounded backoff

A call to another service can fail for a moment, because a connection dropped or a dependency was busy, and succeed a moment later. A **retry executor** calls the operation again. It must not hammer a struggling dependency, so it **waits between attempts**, the wait **grows exponentially**, and the wait **never exceeds a cap**.

In a microservice system this is the simplest resilience pattern. The SpringLab Microservices Lab shows it in the experiment `res-retry-backoff`; here you write the logic yourself. This exercise is plain Java: it runs on a sandboxed worker and does not deploy or call any service.

### Your task

Implement the method `RetryExecutor.execute`:

```java
public static <T> T execute(Callable<T> operation,
                            int maxAttempts,
                            long baseDelayMillis,
                            long maxDelayMillis,
                            Sleeper sleeper) throws Exception
```

Two types are **provided**. Do not declare them (a source that declares any top-level class other than `RetryExecutor` is rejected):

```java
public interface Sleeper {
    void sleep(long millis) throws InterruptedException;
}

public class TransientException extends Exception {
    public TransientException(String message) { super(message); }
}
```

### Required behaviour

1. **Attempts.** `maxAttempts` counts the first call. `maxAttempts = 1` means no retry. If `maxAttempts < 1`, throw `IllegalArgumentException` before calling `operation` at all.
2. **Success.** Call `operation`. If it returns a value, return that value at once.
3. **Transient failure.** If `operation` throws `TransientException` and attempts remain, wait by calling `sleeper.sleep(delay)` and then call `operation` again.
4. **Delay.** The wait before the *k*-th retry (k = 1 for the first retry) is `min(baseDelayMillis * 2^(k-1), maxDelayMillis)`. With base 100 and maximum 800 the waits are 100, 200, 400, 800, 800, and so on. If `baseDelayMillis` is already above `maxDelayMillis`, every wait is `maxDelayMillis`.
5. **Exhaustion.** When the last allowed attempt fails with `TransientException`, rethrow **that same exception object**. Do not wait after the last failure.
6. **Anything else.** Any other exception thrown by `operation` is not a transient failure: let it propagate at once, without retrying and without waiting.
7. **No overflow.** The delay must be correct for any `long` values: doubling many times must not wrap around to a negative or smaller number.
8. **Waiting.** Wait only through `sleeper`. Never call `Thread.sleep`, so that tests are fast and deterministic.

### Submitting

* Write your solution as a single Java 17 source file that declares `public final class RetryExecutor` (nested classes are allowed).
* **Run** compiles your code and runs the sample tests only, so you can debug. It is not an official attempt.
* **Submit** runs every test, including hidden ones, records the result and updates your progress. Hidden tests check the behaviour described above, including edge cases; their inputs and expected values are never shown.
',
 '["Count attempts correctly: the first call is attempt 1, and the limit includes it.", "Compute a bounded exponential backoff: base, 2 x base, 4 x base and so on, never above the maximum delay.", "Recover when a transient failure is followed by success, and stop retrying when attempts run out.", "Propagate exceptions correctly: rethrow the last transient failure once exhausted, and do not retry anything else.", "Avoid arithmetic overflow when the delay is doubled many times.", "Make timing testable by waiting only through an injected Sleeper, never with Thread.sleep."]',
 '* `maxAttempts` can be zero or negative (that is an error, see rule 1) or any positive `int`.
* `baseDelayMillis` and `maxDelayMillis` are non-negative `long` values and may be very large (up to about 2^61).
* `operation` may return any value, throw `TransientException`, or throw another exception. It is called at most `maxAttempts` times.
* `sleeper.sleep` is called only between attempts, never before the first call and never after the last.',
 '[{"title": "Succeeds on the first call", "input": "maxAttempts = 3, baseDelayMillis = 100, maxDelayMillis = 800; the operation returns \\"ok\\"", "output": "returns \\"ok\\"; the operation ran 1 time; sleeper was never called", "explanation": "Nothing failed, so there is nothing to retry and no reason to wait."}, {"title": "Recovers after two transient failures", "input": "maxAttempts = 4, baseDelayMillis = 100, maxDelayMillis = 800; the operation throws TransientException, throws TransientException, then returns \\"ok\\"", "output": "returns \\"ok\\"; the operation ran 3 times; sleeper was called with 100 and then 200", "explanation": "After the first failure the wait is 100 (= base x 2^0); after the second it is 200 (= base x 2^1). The third call succeeds, so no further wait happens."}, {"title": "Gives up when attempts run out", "input": "maxAttempts = 3, baseDelayMillis = 100, maxDelayMillis = 800; the operation always throws TransientException", "output": "the last TransientException is rethrown; the operation ran 3 times; sleeper was called with 100 and then 200", "explanation": "There are three attempts but only two waits: nobody waits after the final failure, because there is no next attempt."}]',
 'import java.util.concurrent.Callable;

public final class RetryExecutor {

    /**
     * Calls {@code operation}, retrying transient failures with a bounded exponential backoff.
     * See the problem statement for the exact rules.
     */
    public static <T> T execute(Callable<T> operation,
                                int maxAttempts,
                                long baseDelayMillis,
                                long maxDelayMillis,
                                Sleeper sleeper) throws Exception {
        // TODO: implement
        throw new UnsupportedOperationException("not implemented yet");
    }
}
', NOW(6));
SET @version = LAST_INSERT_ID();
INSERT INTO coding_test_cases (problem_version_id, sort_order, name, concept, visible, input_spec, expected_spec) VALUES
 (@version, 0, 'Returns the first successful result', 'basic flow', TRUE, '{"maxAttempts": 3, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["R:ok"]}', '{"kind": "RETURN", "exceptionClass": "", "identity": -1, "calls": 1, "sleeps": [], "value": "ok"}'),
 (@version, 1, 'Retries transient failures with doubling delays', 'backoff sequence', TRUE, '{"maxAttempts": 4, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["T:a", "T:b", "R:ok"]}', '{"kind": "RETURN", "exceptionClass": "", "identity": -1, "calls": 3, "sleeps": [100, 200], "value": "ok"}'),
 (@version, 2, 'Gives up after maxAttempts and rethrows the last failure', 'exhaustion', TRUE, '{"maxAttempts": 3, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["T:a", "T:b", "T:c"]}', '{"kind": "THROW", "exceptionClass": "TransientException", "identity": 2, "calls": 3, "sleeps": [100, 200], "value": ""}'),
 (@version, 3, 'Delay is capped at the maximum', 'delay cap', FALSE, '{"maxAttempts": 6, "baseDelayMillis": 100, "maxDelayMillis": 300, "steps": ["T:a", "T:b", "T:c", "T:d", "T:e", "R:ok"]}', '{"kind": "RETURN", "exceptionClass": "", "identity": -1, "calls": 6, "sleeps": [100, 200, 300, 300, 300], "value": "ok"}'),
 (@version, 4, 'A non-transient exception is not retried', 'retry rule', FALSE, '{"maxAttempts": 5, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["O:boom"]}', '{"kind": "THROW", "exceptionClass": "java.lang.IllegalStateException", "identity": 0, "calls": 1, "sleeps": [], "value": ""}'),
 (@version, 5, 'maxAttempts = 1 means no retry', 'attempt counting', FALSE, '{"maxAttempts": 1, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["T:x"]}', '{"kind": "THROW", "exceptionClass": "TransientException", "identity": 0, "calls": 1, "sleeps": [], "value": ""}'),
 (@version, 6, 'No sleep after the final failure', 'last attempt', FALSE, '{"maxAttempts": 2, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["T:a", "T:b"]}', '{"kind": "THROW", "exceptionClass": "TransientException", "identity": 1, "calls": 2, "sleeps": [100], "value": ""}'),
 (@version, 7, 'maxAttempts below 1 is rejected', 'validation', FALSE, '{"maxAttempts": 0, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["R:never"]}', '{"kind": "THROW", "exceptionClass": "java.lang.IllegalArgumentException", "identity": -1, "calls": 0, "sleeps": [], "value": ""}'),
 (@version, 8, 'Very large delays do not overflow', 'overflow safety', FALSE, '{"maxAttempts": 71, "baseDelayMillis": 1099511627776, "maxDelayMillis": 2305843009213693952, "steps": ["T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "T:x", "R:late"]}', '{"kind": "RETURN", "exceptionClass": "", "identity": -1, "calls": 71, "sleeps": [1099511627776, 2199023255552, 4398046511104, 8796093022208, 17592186044416, 35184372088832, 70368744177664, 140737488355328, 281474976710656, 562949953421312, 1125899906842624, 2251799813685248, 4503599627370496, 9007199254740992, 18014398509481984, 36028797018963968, 72057594037927936, 144115188075855872, 288230376151711744, 576460752303423488, 1152921504606846976, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952, 2305843009213693952], "value": "late"}'),
 (@version, 9, 'A non-transient failure after a transient one', 'retry rule', FALSE, '{"maxAttempts": 4, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["T:a", "O:b", "R:ok"]}', '{"kind": "THROW", "exceptionClass": "java.lang.IllegalStateException", "identity": 1, "calls": 2, "sleeps": [100], "value": ""}'),
 (@version, 10, 'Success on the last allowed attempt', 'attempt counting', FALSE, '{"maxAttempts": 3, "baseDelayMillis": 100, "maxDelayMillis": 800, "steps": ["T:a", "T:b", "R:fine"]}', '{"kind": "RETURN", "exceptionClass": "", "identity": -1, "calls": 3, "sleeps": [100, 200], "value": "fine"}'),
 (@version, 11, 'A base delay above the cap is cut to the cap', 'delay cap', FALSE, '{"maxAttempts": 3, "baseDelayMillis": 500, "maxDelayMillis": 200, "steps": ["T:a", "T:b", "R:ok"]}', '{"kind": "RETURN", "exceptionClass": "", "identity": -1, "calls": 3, "sleeps": [200, 200], "value": "ok"}');
