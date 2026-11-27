import java.lang.reflect.Method;

/**
 * Operations: new T D (threshold, open duration), at MS (set the fake clock), allow, ok, fail, state.
 * Observations: "-" for new/at/ok/fail, true/false for allow, the state name for state, or EX:name when the learner's code throws.
 */
final class CircuitBreakerAdapter extends Adapter {

    private Class<?> type;
    private Object breaker;
    private long now;
    private final Clock clock = () -> now;

    @Override
    String className() {
        return "CircuitBreaker";
    }

    @Override
    String check(Class<?> learner) {
        try {
            learner.getConstructor(int.class, long.class, Clock.class);
            for (String m : new String[]{"allowRequest", "recordSuccess", "recordFailure", "state"}) {
                learner.getMethod(m);
            }
            return null;
        } catch (NoSuchMethodException e) {
            return "CircuitBreaker must declare: public CircuitBreaker(int failureThreshold, long openMillis, Clock clock) and the public methods "
                    + "boolean allowRequest(), void recordSuccess(), void recordFailure() and State state()";
        }
    }

    @Override
    void begin(Class<?> learner) {
        type = learner;
        breaker = null;
        now = 0;
    }

    private Object call(String method) throws Exception {
        if (breaker == null) {
            throw new IllegalStateException("NoInstance");
        }
        Method m = type.getMethod(method);
        return m.invoke(breaker);
    }

    @Override
    String step(String[] t) throws Exception {
        switch (t[0]) {
            case "new":
                breaker = null;
                breaker = type.getConstructor(int.class, long.class, Clock.class).newInstance(Integer.parseInt(need(t, 2)), Long.parseLong(need(t, 3)), clock);
                return "-";
            case "at":
                now = Long.parseLong(need(t, 2));
                return "-";
            case "allow":
                return String.valueOf(call("allowRequest"));
            case "ok":
                call("recordSuccess");
                return "-";
            case "fail":
                call("recordFailure");
                return "-";
            case "state":
                return String.valueOf(call("state"));
            default:
                throw new BadScript();
        }
    }
}
