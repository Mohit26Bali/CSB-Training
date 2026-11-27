import java.util.concurrent.Callable;

/**
 * A "duplicate that arrives while the first request is still running" is simulated deterministically: the running operation itself calls
 * the handler again (the "dup" behaviour), which is exactly what a concurrent duplicate would observe.
 *
 * Operations: new TTL, at MS (fake clock), handle KEY BEHAVIOUR, runs.
 *   KEY is a word, or "empty" for the empty string.
 *   BEHAVIOUR is ret:VALUE (the operation returns VALUE), fail (it throws), or dup:KEY2 (while running, it calls handle(KEY2, ...)
 *   and returns "dup=" followed by what that nested call observed).
 * Observations: VALUE#new when the operation ran in this call, VALUE#cached when a stored result was returned, FAILED when the operation's own
 * exception came out, EX:name for any other exception (EX:RequestInProgressException for a duplicate of a running request); runs gives the
 * total number of times any operation ran.
 */
final class IdempotencyAdapter extends Adapter {

    private Class<?> type;
    private Object handler;
    private long now;
    private int executed;
    private final Clock clock = () -> now;

    @Override
    String className() {
        return "IdempotencyHandler";
    }

    @Override
    String check(Class<?> learner) {
        try {
            learner.getConstructor(long.class, Clock.class);
            learner.getMethod("handle", String.class, Callable.class);
            return null;
        } catch (NoSuchMethodException e) {
            return "IdempotencyHandler must declare: public IdempotencyHandler(long ttlMillis, Clock clock) and "
                    + "public <T> T handle(String key, Callable<T> operation) throws Exception";
        }
    }

    @Override
    void begin(Class<?> learner) {
        type = learner;
        handler = null;
        now = 0;
        executed = 0;
    }

    private static String key(String token) {
        return token.equals("empty") ? "" : token;
    }

    /** What the operation of one handle call did, so the call can say whether it ran. */
    private final class Op implements Callable<Object> {
        final String behaviour;
        boolean ran;
        final IllegalStateException boom = new IllegalStateException("boom");

        Op(String behaviour) {
            this.behaviour = behaviour;
        }

        @Override
        public Object call() throws Exception {
            ran = true;
            executed++;
            if (behaviour.equals("fail")) {
                throw boom;
            }
            if (behaviour.startsWith("dup:")) {
                return "dup=" + observe(key(behaviour.substring(4)), new Op("ret:inner"));
            }
            if (behaviour.startsWith("ret:")) {
                return behaviour.substring(4);
            }
            throw new BadScript();
        }
    }

    private String observe(String key, Op op) {
        try {
            Object result = type.getMethod("handle", String.class, Callable.class).invoke(handler, key, op);
            return result + (op.ran ? "#new" : "#cached");
        } catch (java.lang.reflect.InvocationTargetException e) {
            return e.getCause() == op.boom ? "FAILED" : "EX:" + name(e.getCause());
        } catch (ReflectiveOperationException e) {
            return "EX:" + name(e);
        }
    }

    @Override
    String step(String[] t) throws Exception {
        switch (t[0]) {
            case "new":
                handler = null;
                handler = type.getConstructor(long.class, Clock.class).newInstance(Long.parseLong(need(t, 2)), clock);
                return "-";
            case "at":
                now = Long.parseLong(need(t, 2));
                return "-";
            case "runs":
                return String.valueOf(executed);
            case "handle": {
                if (handler == null) {
                    throw new IllegalStateException("NoInstance");
                }
                String behaviour = need(t, 3);
                if (!(behaviour.equals("fail") || behaviour.startsWith("ret:") || behaviour.startsWith("dup:"))) {
                    throw new BadScript();
                }
                return observe(key(need(t, 2)), new Op(behaviour));
            }
            default:
                throw new BadScript();
        }
    }
}
