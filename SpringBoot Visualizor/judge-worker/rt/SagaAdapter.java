import java.util.ArrayList;
import java.util.List;

/**
 * Operations: new STEP... (each step is NAME=EXEC/COMP where EXEC and COMP are ok or fail; "-" means no steps), run, calls, pending.
 * The adapter's steps record every call made on them, so the observation is the ground truth of what the learner's coordinator did.
 * Observations: the status name for run; the calls as execute:A,execute:B,compensate:A (or "-") for calls; the names whose compensation
 * failed (or "-") for pending; "-" for new; EX:name when the learner's code throws.
 */
final class SagaAdapter extends Adapter {

    private Class<?> type;
    private Object saga;
    private final List<String> calls = new ArrayList<>();

    @Override
    String className() {
        return "SagaCoordinator";
    }

    @Override
    String check(Class<?> learner) {
        try {
            learner.getConstructor(List.class);
            learner.getMethod("run");
            learner.getMethod("pendingCompensations");
            return null;
        } catch (NoSuchMethodException e) {
            return "SagaCoordinator must declare: public SagaCoordinator(List<SagaStep> steps), public SagaStatus run() "
                    + "and public List<String> pendingCompensations()";
        }
    }

    @Override
    void begin(Class<?> learner) {
        type = learner;
        saga = null;
        calls.clear();
    }

    private SagaStep step(String spec) {
        int eq = spec.indexOf('=');
        int slash = spec.indexOf('/');
        if (eq < 1 || slash < eq) {
            throw new BadScript();
        }
        String name = spec.substring(0, eq);
        boolean executeFails = spec.substring(eq + 1, slash).equals("fail");
        boolean compensateFails = spec.substring(slash + 1).equals("fail");
        return new SagaStep() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public void execute() throws Exception {
                calls.add("execute:" + name);
                if (executeFails) {
                    throw new IllegalStateException("execute failed: " + name);
                }
            }

            @Override
            public void compensate() throws Exception {
                calls.add("compensate:" + name);
                if (compensateFails) {
                    throw new IllegalStateException("compensate failed: " + name);
                }
            }
        };
    }

    private static String join(List<?> items) {
        if (items == null) {
            return "null";
        }
        if (items.isEmpty()) {
            return "-";
        }
        StringBuilder b = new StringBuilder();
        for (Object o : items) {
            b.append(b.length() == 0 ? "" : ",").append(o);
        }
        return b.toString();
    }

    @Override
    String step(String[] t) throws Exception {
        switch (t[0]) {
            case "new": {
                saga = null;
                List<SagaStep> steps = new ArrayList<>();
                for (int i = 1; i < t.length; i++) {
                    if (!t[i].equals("-")) {
                        steps.add(step(t[i]));
                    }
                }
                saga = type.getConstructor(List.class).newInstance(steps);
                return "-";
            }
            case "run":
                return String.valueOf(call("run"));
            case "calls":
                return join(calls);
            case "pending":
                return join((List<?>) call("pendingCompensations"));
            default:
                throw new BadScript();
        }
    }

    private Object call(String method) throws Exception {
        if (saga == null) {
            throw new IllegalStateException("NoInstance");
        }
        return type.getMethod(method).invoke(saga);
    }
}
