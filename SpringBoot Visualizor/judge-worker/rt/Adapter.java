import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

/**
 * A trusted adapter between the driver's scripted tests and ONE learner contract. It runs INSIDE the sandbox next to the learner's code:
 * it builds the objects the learner's class needs (fake clock, fake store, scripted steps), executes the script's operations one by one
 * through the learner's public API, and reports what it observed, ONE line per operation. It knows nothing about expected results: the
 * driver compares the observed trace with the expected trace it keeps outside the sandbox.
 *
 * <p>A script is a list of operations, each a line of space-separated tokens. The adapter, not the learner, defines what each operation
 * means; an unknown operation is reported as {@code EX:BadScript} (the driver also validates every script before sending it).
 */
abstract class Adapter {

    /** The simple name of the class the learner must declare in the one source file. */
    abstract String className();

    /** A message describing what is missing from the learner's class (reported as a compile error), or null if it can be used. */
    abstract String check(Class<?> learner);

    /** Called once per script, before the first operation. */
    abstract void begin(Class<?> learner) throws Exception;

    /** Executes one operation and returns its one-line observation. */
    abstract String step(String[] tokens) throws Exception;

    /** Called once per script after the last operation (release anything an operation left waiting). */
    void end() {
    }

    final List<String> run(Class<?> learner, List<String> script) throws Exception {
        begin(learner);
        List<String> trace = new ArrayList<>();
        try {
            for (String line : script) {
                String[] tokens = line.trim().split("\\s+");
                String observed;
                try {
                    observed = step(tokens);
                } catch (InvocationTargetException e) {
                    observed = "EX:" + name(e.getCause());
                } catch (BadScript e) {
                    observed = "EX:BadScript";
                } catch (Throwable e) {
                    observed = "EX:" + name(e);
                }
                trace.add(observed);
            }
        } finally {
            end();
        }
        return trace;
    }

    static String name(Throwable t) {
        if (t == null) {
            return "null";
        }
        String simple = t.getClass().getSimpleName();
        return simple.isEmpty() ? t.getClass().getName() : simple;
    }

    /** An operation the adapter does not define. */
    static final class BadScript extends RuntimeException {
        BadScript() {
            super("bad script");
        }
    }

    static String need(String[] t, int n) {
        if (t.length < n) {
            throw new BadScript();
        }
        return t[n - 1];
    }
}
