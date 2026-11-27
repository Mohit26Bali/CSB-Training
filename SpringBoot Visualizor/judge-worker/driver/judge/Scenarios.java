package judge;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * The tests of the pilot problem, "RetryExecutor". They live ONLY in the trusted driver: the sandbox is told the scenario INPUT
 * (attempts, delays, the scripted behaviour of the action) and answers with what it observed. The expected observation is computed
 * here by a reference model and is never sent to the sandbox.
 */
final class Scenarios {

    /**
     * One test as the driver sees it. {@code wire} is the line sent INTO the sandbox (the input only); {@code check} compares the observation
     * that comes back with the expectation the driver keeps. check returns null when they match, or a message; it throws
     * IllegalArgumentException when the answer is not even well-formed (then the test is an error, never a pass).
     */
    interface Case {
        String name();

        boolean visible();

        String wire(int id);

        String check(String frame, int id);
    }

    record Step(char kind, String text) {
        String wire() {
            return kind + ":" + text;
        }
    }

    record Expected(String kind, String value, String exClass, int ident, int calls, List<Long> sleeps) {
    }

    record Observed(String kind, String exClass, int ident, int calls, List<Long> sleeps, String value) {
    }

    /**
     * @param fixed when present, the expected observation supplied by the caller (the backend, which owns the tests); when null the
     *              built-in reference model computes it (the CLI and the adversarial suite use the built-in tests)
     */
    record Scenario(String name, boolean visible, String concept, int maxAttempts, long base, long cap, List<Step> steps, Expected fixed) implements Case {

        Scenario(String name, boolean visible, String concept, int maxAttempts, long base, long cap, List<Step> steps) {
            this(name, visible, concept, maxAttempts, base, cap, steps, null);
        }

        @Override
        public String check(String frame, int id) {
            Observed observed = parseObservation(frame, id);
            if (observed == null) {
                throw new IllegalArgumentException("malformed observation");
            }
            return compare(expected(), observed);
        }

        @Override
        public String wire(int id) {
            StringBuilder s = new StringBuilder();
            for (Step st : steps) {
                s.append(s.length() == 0 ? "" : ",").append(st.wire());
            }
            return "RUN " + id + " " + maxAttempts + " " + base + " " + cap + " " + (s.length() == 0 ? "-" : s);
        }

        /** The reference model of the exercise: what a correct RetryExecutor does for this input. */
        Expected expected() {
            if (fixed != null) {
                return fixed;
            }
            if (maxAttempts < 1) {
                return new Expected("THROW", "", "java.lang.IllegalArgumentException", -1, 0, List.of());
            }
            List<Long> sleeps = new ArrayList<>();
            int made = 0;
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                Step step = attempt <= steps.size() ? steps.get(attempt - 1) : new Step('O', "unscripted");
                if (step.kind() == 'R') {
                    return new Expected("RETURN", step.text(), "", -1, attempt, sleeps);
                }
                made++;
                if (step.kind() == 'O') {
                    return new Expected("THROW", "", "java.lang.IllegalStateException", made - 1, attempt, sleeps);
                }
                if (attempt == maxAttempts) {
                    return new Expected("THROW", "", "TransientException", made - 1, attempt, sleeps);
                }
                sleeps.add(delayBeforeRetry(attempt));
            }
            throw new IllegalStateException();
        }

        /** min(base * 2^(k-1), cap) for the k-th retry, computed without overflow. */
        long delayBeforeRetry(int k) {
            BigInteger d = BigInteger.valueOf(base).shiftLeft(k - 1);
            return d.min(BigInteger.valueOf(cap)).longValueExact();
        }
    }

    /**
     * Parses one test line sent by the backend:
     * {@code TEST idx visible(0|1) b64name maxAttempts base cap steps|- kind b64exClass|- ident calls sleeps|- b64value|-}.
     * Everything is validated; any problem is an IllegalArgumentException (the caller then reports an internal error, never a pass).
     */
    static Scenario fromWire(String line) {
        String[] f = line.trim().split(" ");
        if (f.length != 14 || !f[0].equals("TEST") || !(f[2].equals("0") || f[2].equals("1"))) {
            throw new IllegalArgumentException("bad test line");
        }
        try {
            List<Step> steps = new ArrayList<>();
            if (!f[7].equals("-")) {
                for (String token : f[7].split(",")) {
                    if (!token.matches("[RTO]:[A-Za-z0-9]{1,20}")) {
                        throw new IllegalArgumentException("bad step");
                    }
                    steps.add(new Step(token.charAt(0), token.substring(2)));
                }
            }
            if (!f[8].equals("RETURN") && !f[8].equals("THROW")) {
                throw new IllegalArgumentException("bad kind");
            }
            List<Long> sleeps = new ArrayList<>();
            if (!f[12].equals("-")) {
                for (String v : f[12].split(",")) {
                    sleeps.add(Long.parseLong(v));
                }
            }
            String name = decode(f[3]);
            if (name.isEmpty() || name.length() > 200) {
                throw new IllegalArgumentException("bad name");
            }
            Expected expected = new Expected(f[8], decode(f[13]), decode(f[9]), Integer.parseInt(f[10]), Integer.parseInt(f[11]), sleeps);
            return new Scenario(name, f[2].equals("1"), "", Integer.parseInt(f[4]), Long.parseLong(f[5]), Long.parseLong(f[6]), steps, expected);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("bad test line");
        }
    }

    private static String decode(String b64) {
        return b64.equals("-") ? "" : new String(java.util.Base64.getUrlDecoder().decode(b64), java.nio.charset.StandardCharsets.UTF_8);
    }

    static Observed parseObservation(String frame, int index) {
        try {
            String[] f = frame.split(" ");
            if (f.length != 8 || !f[0].equals("OBS") || Integer.parseInt(f[1]) != index) {
                return null;
            }
            List<Long> sleeps = new ArrayList<>();
            if (!f[6].equals("-")) {
                for (String v : f[6].split(",")) {
                    sleeps.add(Long.parseLong(v));
                }
            }
            if (!f[2].equals("RETURN") && !f[2].equals("THROW")) {
                return null;
            }
            return new Observed(f[2], decode(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), sleeps, decode(f[7]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final java.util.regex.Pattern SCRIPT_LINE = java.util.regex.Pattern.compile("[A-Za-z0-9 :_.,=/@\\-]{1,120}");
    private static final java.util.regex.Pattern TRACE_LINE = java.util.regex.Pattern.compile("[A-Za-z0-9 :_.,=/#@\\-]{1,200}");

    /**
     * A scripted test: the script is run by the sandbox's adapter, one operation per line, and the expected trace has exactly one line per
     * operation. The expected trace is kept here, outside the sandbox.
     */
    record ScriptCase(String name, boolean visible, List<String> script, List<String> expected) implements Case {
        @Override
        public String wire(int id) {
            return "RUN " + id + " " + java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(String.join("\n", script).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public String check(String frame, int id) {
            String[] f = frame.split(" ");
            if (f.length != 3 || !f[0].equals("OBS")) {
                throw new IllegalArgumentException("malformed observation");
            }
            try {
                if (Integer.parseInt(f[1]) != id) {
                    throw new IllegalArgumentException("wrong test");
                }
                String[] got = new String(java.util.Base64.getUrlDecoder().decode(f[2]), java.nio.charset.StandardCharsets.UTF_8).split("\n", -1);
                for (int i = 0; i < Math.min(got.length, expected.size()); i++) {
                    if (!got[i].equals(expected.get(i))) {
                        return "step " + (i + 1) + " (" + script.get(i) + "): expected " + expected.get(i) + " but got " + (got[i].length() > 80 ? got[i].substring(0, 80) + "..." : got[i]);
                    }
                }
                if (got.length != expected.size()) {
                    return "expected " + expected.size() + " observations but got " + got.length;
                }
                return null;
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("malformed observation");
            }
        }
    }

    /**
     * Parses {@code SCRIPT idx visible(0|1) b64name b64script b64trace} for the given exercise. The script may only use the operations of that
     * exercise, every line is restricted to a small alphabet, and the trace must have exactly one line per operation.
     */
    static ScriptCase fromScriptWire(String line, Harness harness) {
        String[] f = line.trim().split(" ");
        if (!harness.scripted() || f.length != 6 || !f[0].equals("SCRIPT") || !(f[2].equals("0") || f[2].equals("1"))) {
            throw new IllegalArgumentException("bad test line");
        }
        try {
            String name = decode(f[3]);
            if (name.isEmpty() || name.length() > 200) {
                throw new IllegalArgumentException("bad name");
            }
            List<String> script = List.of(decode(f[4]).split("\n", -1));
            List<String> trace = List.of(decode(f[5]).split("\n", -1));
            if (script.isEmpty() || script.size() > 200 || trace.size() != script.size()) {
                throw new IllegalArgumentException("bad script");
            }
            for (String op : script) {
                if (!SCRIPT_LINE.matcher(op).matches() || !harness.operations().contains(op.trim().split(" ")[0])) {
                    throw new IllegalArgumentException("bad operation");
                }
            }
            for (String t : trace) {
                if (!TRACE_LINE.matcher(t).matches()) {
                    throw new IllegalArgumentException("bad trace");
                }
            }
            return new ScriptCase(name, f[2].equals("1"), script, trace);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("bad test line");
        }
    }

    private static Step r(String t) {
        return new Step('R', t);
    }

    private static Step t(String t) {
        return new Step('T', t);
    }

    private static Step o(String t) {
        return new Step('O', t);
    }

    static List<Scenario> all() {
        List<Step> seventyTransient = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            seventyTransient.add(t("x"));
        }
        seventyTransient.add(r("late"));
        return List.of(
                new Scenario("returns the first successful result", true, "basic flow", 3, 100, 800, List.of(r("ok"))),
                new Scenario("retries transient failures with doubling delays", true, "backoff sequence", 4, 100, 800, List.of(t("a"), t("b"), r("ok"))),
                new Scenario("gives up after maxAttempts and rethrows the last failure", true, "exhaustion", 3, 100, 800, List.of(t("a"), t("b"), t("c"))),
                new Scenario("delay is capped at the maximum", false, "cap", 6, 100, 300, List.of(t("a"), t("b"), t("c"), t("d"), t("e"), r("ok"))),
                new Scenario("a non-transient exception is not retried", false, "retry rule", 5, 100, 800, List.of(o("boom"))),
                new Scenario("maxAttempts = 1 means no retry", false, "attempt counting", 1, 100, 800, List.of(t("x"))),
                new Scenario("no sleep after the final failure", false, "last attempt", 2, 100, 800, List.of(t("a"), t("b"))),
                new Scenario("maxAttempts below 1 is rejected", false, "validation", 0, 100, 800, List.of(r("never"))),
                new Scenario("very large delays do not overflow", false, "robustness", 71, 1L << 40, 1L << 61, seventyTransient),
                new Scenario("a non-transient failure after a transient one", false, "retry rule", 4, 100, 800, List.of(t("a"), o("b"), r("ok"))),
                new Scenario("success on the last allowed attempt", false, "attempt counting", 3, 100, 800, List.of(t("a"), t("b"), r("fine"))),
                new Scenario("a base delay above the cap is cut to the cap", false, "cap", 3, 500, 200, List.of(t("a"), t("b"), r("ok"))));
    }

    /** Null if the observation matches; otherwise a message. Only VISIBLE tests ever show the message to a learner. */
    static String compare(Expected e, Observed o) {
        if (!e.kind().equals(o.kind())) {
            return "expected a " + e.kind().toLowerCase() + " but the method " + (o.kind().equals("RETURN") ? "returned a value" : "threw " + o.exClass());
        }
        if (e.kind().equals("RETURN") && !e.value().equals(o.value())) {
            return "expected the result \"" + e.value() + "\" but got \"" + o.value() + "\"";
        }
        if (e.kind().equals("THROW")) {
            if (!e.exClass().equals(o.exClass())) {
                return "expected " + e.exClass() + " but got " + o.exClass();
            }
            if (e.ident() != o.ident()) {
                return "the exception thrown is not the one the action threw last (it was wrapped, replaced or an earlier one)";
            }
        }
        if (e.calls() != o.calls()) {
            return "the action was called " + o.calls() + " time(s); expected " + e.calls();
        }
        if (!e.sleeps().equals(o.sleeps())) {
            return "expected waits " + e.sleeps() + " but got " + o.sleeps();
        }
        return null;
    }
}
