package com.springlab.lab.micro.core;

import java.util.EnumMap;
import java.util.Map;

/**
 * Controllable behaviour of each simulated service, for ONE run (never global). This is how What-If controls make a service
 * unavailable, slow, lossy or buggy deterministically.
 */
public final class ServiceBehaviours {

    public enum Mode {
        UP,
        /** Connection refused: the request is never processed. */
        DOWN,
        /** The service processes the request (side effects happen) but answers late. */
        DELAYED,
        /** The request is lost on the way: never processed and never answered. */
        REQUEST_LOST,
        /** The service fails with an unexpected exception before doing any work. */
        ERROR
    }

    /** {@code firstCalls} &gt; 0 applies the mode only to that many calls, then the service is UP again (a transient fault). */
    public record Behaviour(Mode mode, int delayMillis, int firstCalls, int afterCalls) {
        public static Behaviour up() {
            return new Behaviour(Mode.UP, 0, 0, 0);
        }

        public static Behaviour down() {
            return new Behaviour(Mode.DOWN, 0, 0, 0);
        }

        public static Behaviour delayed(int delayMillis) {
            return new Behaviour(Mode.DELAYED, delayMillis, 0, 0);
        }

        public static Behaviour requestLost() {
            return new Behaviour(Mode.REQUEST_LOST, 0, 0, 0);
        }

        public static Behaviour error() {
            return new Behaviour(Mode.ERROR, 0, 0, 0);
        }

        /** UP for the first {@code calls} calls, DOWN from then on: a service that fails in the middle of a workflow. */
        public static Behaviour downAfter(int calls) {
            return new Behaviour(Mode.DOWN, 0, 0, calls);
        }

        public static Behaviour errorFirst(int calls) {
            return new Behaviour(Mode.ERROR, 0, calls, 0);
        }
    }

    /** What applies to ONE call. */
    public record Applied(Mode mode, int delayMillis) {
    }

    private final Map<ServiceName, Behaviour> behaviours = new EnumMap<>(ServiceName.class);
    private final Map<ServiceName, Integer> callsSeen = new EnumMap<>(ServiceName.class);
    private final Map<String, Behaviour> instanceBehaviours = new java.util.HashMap<>();
    private final Map<String, Integer> instanceCallsSeen = new java.util.HashMap<>();

    public void set(ServiceName service, Behaviour behaviour) {
        behaviours.put(service, behaviour);
        callsSeen.put(service, 0);
    }

    public void reset(ServiceName service) {
        set(service, Behaviour.up());
    }

    public Behaviour current(ServiceName service) {
        return behaviours.getOrDefault(service, Behaviour.up());
    }

    /** Behaviour of ONE instance of a service. It overrides the service-level behaviour for calls aimed at that instance. */
    public void setInstance(ServiceName service, String instance, Behaviour behaviour) {
        instanceBehaviours.put(service.id() + "/" + instance, behaviour);
        instanceCallsSeen.put(service.id() + "/" + instance, 0);
    }

    Applied next(ServiceName service, String instance) {
        String key = instance == null ? null : service.id() + "/" + instance;
        if (key != null && instanceBehaviours.containsKey(key)) {
            return apply(instanceBehaviours.get(key), instanceCallsSeen.merge(key, 1, Integer::sum));
        }
        return next(service);
    }

    Applied next(ServiceName service) {
        return apply(current(service), callsSeen.merge(service, 1, Integer::sum));
    }

    /** {@code seen} is the number of this call (1-based). The first {@code afterCalls} are UP; then the mode applies, for {@code firstCalls} calls or for ever. */
    private static Applied apply(Behaviour b, int seen) {
        if (seen <= b.afterCalls()) {
            return new Applied(Mode.UP, 0);
        }
        int index = seen - b.afterCalls();
        if (b.firstCalls() > 0 && index > b.firstCalls()) {
            return new Applied(Mode.UP, 0);
        }
        return new Applied(b.mode(), b.delayMillis());
    }
}
