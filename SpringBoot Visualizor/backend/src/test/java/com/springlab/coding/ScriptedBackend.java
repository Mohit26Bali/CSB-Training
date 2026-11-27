package com.springlab.coding;

import com.springlab.coding.execution.Availability;
import com.springlab.coding.execution.ExecutionBackend;
import com.springlab.coding.execution.ExecutionRequest;
import com.springlab.coding.execution.ExecutionResult;
import com.springlab.coding.execution.Verdict;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

/**
 * An execution backend for the API tests. It returns whatever the test scripts for the next request, and can hold a job until the test
 * releases it. It stands in for the worker only: everything else (admission, queue, persistence, verdict reconciliation) is the real code.
 */
public class ScriptedBackend implements ExecutionBackend {

    /** What the backend does with a request: build the result the "worker" would answer. */
    public volatile BiFunction<String, ExecutionRequest, ExecutionResult> script = (id, r) -> allPass(r);
    public volatile Availability availability = Availability.up("scripted");
    public volatile CountDownLatch gate;
    public final Map<String, ExecutionRequest> seen = new ConcurrentHashMap<>();
    public final List<String> order = new java.util.concurrent.CopyOnWriteArrayList<>();
    public volatile ExecutionRequest last;

    public void reset() {
        script = (id, r) -> allPass(r);
        availability = Availability.up("scripted");
        gate = null;
        seen.clear();
        order.clear();
        last = null;
    }

    @Override
    public String name() {
        return "scripted";
    }

    @Override
    public Availability availability() {
        return availability;
    }

    @Override
    public ExecutionResult execute(String jobId, ExecutionRequest request, Duration hardTimeout) throws InterruptedException {
        seen.put(jobId, request);
        order.add(jobId);
        last = request;
        CountDownLatch g = gate;
        if (g != null) {
            g.await(60, TimeUnit.SECONDS);
        }
        return script.apply(jobId, request);
    }

    @Override
    public void cancel(String jobId) {
        // the service interrupts the worker thread; nothing else to stop here
    }

    // ------------------------------------------------------------------------------------------------ canned answers

    /** The answer of a worker that judged exactly the tests it was sent, and every one of them passed. */
    public static ExecutionResult allPass(ExecutionRequest r) {
        List<ExecutionResult.TestOutcome> tests = new ArrayList<>();
        for (int i = 0; i < r.tests().size(); i++) {
            tests.add(new ExecutionResult.TestOutcome("t" + i, r.tests().get(i).visible(), "PASS", null));
        }
        return new ExecutionResult(Verdict.ACCEPTED, tests, tests.size(), tests.size(), "", 1900, 79_000, 1_800);
    }

    /** Every visible test passes and the first hidden one fails (a worker that is "too nice" would leak a message here, which must be dropped). */
    public static ExecutionResult hiddenFails(ExecutionRequest r) {
        List<ExecutionResult.TestOutcome> tests = new ArrayList<>();
        boolean failed = false;
        int passed = 0;
        for (int i = 0; i < r.tests().size(); i++) {
            boolean visible = r.tests().get(i).visible();
            boolean fail = !visible && !failed;
            failed |= fail;
            if (!fail) {
                passed++;
            }
            tests.add(new ExecutionResult.TestOutcome("t" + i, visible, fail ? "FAIL" : "PASS", fail ? "LEAKED-HIDDEN-DETAIL expected 42" : null));
        }
        return new ExecutionResult(Verdict.WRONG_ANSWER, tests, passed, tests.size(), "", 1900, 79_000, 1_800);
    }

    /** The first visible test fails with a message; everything after it passes. */
    public static ExecutionResult visibleFails(ExecutionRequest r) {
        List<ExecutionResult.TestOutcome> tests = new ArrayList<>();
        boolean done = false;
        int passed = 0;
        for (int i = 0; i < r.tests().size(); i++) {
            boolean visible = r.tests().get(i).visible();
            boolean fail = visible && !done;
            done |= fail;
            if (!fail) {
                passed++;
            }
            tests.add(new ExecutionResult.TestOutcome("t" + i, visible, fail ? "FAIL" : "PASS", fail ? "expected waits [100, 200] but got []" : null));
        }
        return new ExecutionResult(Verdict.WRONG_ANSWER, tests, passed, tests.size(), "", 1900, 79_000, 1_800);
    }

    /** A verdict that comes with every test NOT_RUN (a compile error, or a stop before the tests). */
    public static ExecutionResult notRun(ExecutionRequest r, Verdict verdict, String compilerOutput) {
        List<ExecutionResult.TestOutcome> tests = new ArrayList<>();
        for (int i = 0; i < r.tests().size(); i++) {
            tests.add(new ExecutionResult.TestOutcome("t" + i, r.tests().get(i).visible(), "NOT_RUN", null));
        }
        return new ExecutionResult(verdict, tests, 0, tests.size(), compilerOutput, 900, -1, -1);
    }
}
