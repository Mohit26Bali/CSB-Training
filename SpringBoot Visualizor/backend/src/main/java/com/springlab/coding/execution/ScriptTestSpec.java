package com.springlab.coding.execution;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

/**
 * A scripted test: a list of operations on the learner's class, and the EXPECTED observation of every operation (one line each).
 * The sandbox's adapter executes the operations and reports what it saw; the trusted driver compares that with {@code trace}.
 *
 * <p>Validation mirrors the worker's: the operations must belong to the exercise's own list, every line is limited to a small alphabet and
 * length, and the trace has exactly one line per operation. A test that breaks a rule is refused here and again by the worker.
 */
public record ScriptTestSpec(String name, boolean visible, List<String> script, List<String> trace) implements WorkerTest {

    private static final Pattern SCRIPT_LINE = Pattern.compile("[A-Za-z0-9 :_.,=/@\\-]{1,120}");
    private static final Pattern TRACE_LINE = Pattern.compile("[A-Za-z0-9 :_.,=/#@\\-]{1,200}");

    public ScriptTestSpec {
        if (name == null || name.isBlank() || name.length() > 200 || name.chars().anyMatch(c -> c < 0x20)) {
            throw new IllegalArgumentException("invalid test name");
        }
        script = script == null ? List.of() : List.copyOf(script);
        trace = trace == null ? List.of() : List.copyOf(trace);
    }

    /** Builds a test for a given exercise, checking the script and the trace against that exercise's own rules. */
    public static ScriptTestSpec of(ExecutionRequest.Problem problem, String name, boolean visible, List<String> script, List<String> trace) {
        validate(problem, script, trace);
        return new ScriptTestSpec(name, visible, script, trace);
    }

    /** Checks the script and the trace against the exercise the test belongs to. */
    public static void validate(ExecutionRequest.Problem problem, List<String> script, List<String> trace) {
        if (!problem.scripted()) {
            throw new IllegalArgumentException("this exercise has no scripted tests");
        }
        if (script == null || trace == null || script.isEmpty() || script.size() > 200 || trace.size() != script.size()) {
            throw new IllegalArgumentException("a script needs 1 to 200 operations and one expected observation for each");
        }
        for (String op : script) {
            if (op == null || !SCRIPT_LINE.matcher(op).matches() || !problem.operations().contains(op.trim().split(" ")[0])) {
                throw new IllegalArgumentException("invalid operation");
            }
        }
        for (String t : trace) {
            if (t == null || !TRACE_LINE.matcher(t).matches()) {
                throw new IllegalArgumentException("invalid expected observation");
            }
        }
    }

    @Override
    public String toWireLine(int index) {
        return "SCRIPT " + index + " " + (visible ? 1 : 0) + " " + b64(name) + " " + b64(String.join("\n", script)) + " " + b64(String.join("\n", trace));
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Test data never appears in a log line or a stray toString. */
    @Override
    public String toString() {
        return "ScriptTestSpec[" + (visible ? "visible" : "hidden") + "]";
    }
}
