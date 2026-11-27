package com.springlab.coding.execution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Turns the single JSON line the worker prints into an {@link ExecutionResult}, and refuses anything that does not look exactly like
 * what the worker produces. The worker is trusted, but its output travels through a pipe, a process and a log-like channel: a
 * truncated, garbled or inconsistent answer must become INTERNAL_ERROR and never a verdict that could be read as a pass.
 */
final class WorkerResponseParser {

    static final class MalformedResponseException extends Exception {
        MalformedResponseException(String message) {
            super(message);
        }
    }

    private static final int MAX_TESTS = 50;
    private static final Set<String> STATUSES = Set.of("PASS", "FAIL", "NOT_RUN", "ERROR", "TIMEOUT");
    private static final Set<Verdict> WORKER_VERDICTS = Set.of(Verdict.ACCEPTED, Verdict.WRONG_ANSWER, Verdict.COMPILE_ERROR,
            Verdict.RUNTIME_ERROR, Verdict.TIME_LIMIT, Verdict.MEMORY_LIMIT, Verdict.OUTPUT_LIMIT, Verdict.INVALID_SUBMISSION, Verdict.INTERNAL_ERROR);

    private final ObjectMapper mapper;

    WorkerResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    ExecutionResult parse(String json) throws MalformedResponseException {
        JsonNode root;
        try {
            root = mapper.readTree(json);
        } catch (Exception e) {
            throw new MalformedResponseException("not JSON");
        }
        if (root == null || !root.isObject()) {
            throw new MalformedResponseException("not an object");
        }
        Verdict verdict;
        try {
            verdict = Verdict.valueOf(text(root, "verdict", 30));
        } catch (IllegalArgumentException e) {
            throw new MalformedResponseException("unknown verdict");
        }
        if (!WORKER_VERDICTS.contains(verdict)) {
            throw new MalformedResponseException("verdict not allowed from the worker");
        }
        int passed = integer(root, "passed", 0, MAX_TESTS);
        int total = integer(root, "total", 0, MAX_TESTS);
        if (passed > total) {
            throw new MalformedResponseException("passed exceeds total");
        }
        JsonNode testsNode = root.get("tests");
        if (testsNode == null || !testsNode.isArray() || testsNode.size() != total) {
            throw new MalformedResponseException("tests do not match total");
        }
        List<ExecutionResult.TestOutcome> tests = new ArrayList<>();
        int passes = 0;
        for (JsonNode t : testsNode) {
            if (!t.isObject()) {
                throw new MalformedResponseException("test is not an object");
            }
            String status = text(t, "status", 10);
            if (!STATUSES.contains(status)) {
                throw new MalformedResponseException("unknown test status");
            }
            JsonNode visibleNode = t.get("visible");
            if (visibleNode == null || !visibleNode.isBoolean()) {
                throw new MalformedResponseException("visible is not a boolean");
            }
            boolean visible = visibleNode.asBoolean();
            JsonNode messageNode = t.get("message");
            String message = null;
            if (messageNode != null && !messageNode.isNull()) {
                if (!messageNode.isTextual() || messageNode.asText().length() > 500) {
                    throw new MalformedResponseException("bad message");
                }
                // A hidden test never carries a message out of this layer, even if the worker sent one.
                message = visible ? clean(messageNode.asText()) : null;
            }
            if (status.equals("PASS")) {
                passes++;
            }
            tests.add(new ExecutionResult.TestOutcome(clean(text(t, "name", 200)), visible, status, message));
        }
        if (passes != passed) {
            throw new MalformedResponseException("passed does not match the test statuses");
        }
        if (verdict == Verdict.ACCEPTED && (total == 0 || passed != total)) {
            throw new MalformedResponseException("ACCEPTED without every test passing");
        }
        if (verdict != Verdict.ACCEPTED && total > 0 && passed == total && verdict == Verdict.WRONG_ANSWER) {
            throw new MalformedResponseException("WRONG_ANSWER with every test passing");
        }
        String compiler = root.hasNonNull("compilerOutput") ? text(root, "compilerOutput", 4000) : "";
        return new ExecutionResult(verdict, tests, passed, total, clean(compiler), longValue(root, "wallMillis", 0, 10_000_000L),
                longValue(root, "peakKb", -1, 100_000_000L), longValue(root, "cpuMillis", -1, 100_000_000L));
    }

    private static String text(JsonNode n, String field, int max) throws MalformedResponseException {
        JsonNode v = n.get(field);
        if (v == null || !v.isTextual() || v.asText().length() > max) {
            throw new MalformedResponseException("bad field " + field);
        }
        return v.asText();
    }

    private static int integer(JsonNode n, String field, int min, int max) throws MalformedResponseException {
        JsonNode v = n.get(field);
        if (v == null || !v.isInt() || v.asInt() < min || v.asInt() > max) {
            throw new MalformedResponseException("bad field " + field);
        }
        return v.asInt();
    }

    private static long longValue(JsonNode n, String field, long min, long max) throws MalformedResponseException {
        JsonNode v = n.get(field);
        if (v == null || !v.canConvertToLong() || !v.isIntegralNumber() || v.asLong() < min || v.asLong() > max) {
            throw new MalformedResponseException("bad field " + field);
        }
        return v.asLong();
    }

    /** Control characters other than newline and tab are dropped so a message cannot disturb a log or a page. */
    private static String clean(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\n' || c == '\t' || c >= 0x20 && c != 0x7f) {
                b.append(c);
            }
        }
        return b.toString();
    }
}
