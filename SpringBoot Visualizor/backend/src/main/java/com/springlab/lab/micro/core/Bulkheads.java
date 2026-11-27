package com.springlab.lab.micro.core;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Bounded concurrency per target service, built on real {@link Semaphore}s: a call that cannot get a permit is rejected without
 * reaching the service. With {@link Mode#PER_SERVICE} every service has its own permits, so a saturated Payment service cannot
 * use up Inventory's capacity (the bulkhead). {@link Mode#SHARED} puts all services behind one pool, which shows the failure the
 * bulkhead prevents. One instance belongs to one run.
 */
public final class Bulkheads {

    public enum Mode { PER_SERVICE, SHARED, UNLIMITED }

    public record Policy(Mode mode, int permits) {
        public Policy {
            if (permits < 1 || permits > 100) {
                throw new IllegalArgumentException("permits out of range");
            }
        }

        public static Policy perService(int permits) {
            return new Policy(Mode.PER_SERVICE, permits);
        }

        public static Policy shared(int permits) {
            return new Policy(Mode.SHARED, permits);
        }

        public static Policy unlimited() {
            return new Policy(Mode.UNLIMITED, 1);
        }
    }

    /** One held permit. Closing it twice releases it once. */
    public static final class Permit implements AutoCloseable {
        private final Semaphore semaphore;
        private final AtomicBoolean released = new AtomicBoolean();

        private Permit(Semaphore semaphore) {
            this.semaphore = semaphore;
        }

        @Override
        public void close() {
            if (semaphore != null && released.compareAndSet(false, true)) {
                semaphore.release();
            }
        }
    }

    private final Policy policy;
    private final Semaphore shared;
    private final Map<ServiceName, Semaphore> perService = new EnumMap<>(ServiceName.class);

    public Bulkheads(Policy policy) {
        this.policy = policy;
        this.shared = policy.mode() == Mode.SHARED ? new Semaphore(policy.permits()) : null;
        if (policy.mode() == Mode.PER_SERVICE) {
            for (ServiceName s : ServiceName.values()) {
                perService.put(s, new Semaphore(policy.permits()));
            }
        }
    }

    public Policy policy() {
        return policy;
    }

    /** A permit, or null when the target is saturated. */
    public Permit tryAcquire(ServiceName target) {
        Semaphore s = semaphoreOf(target);
        if (s == null) {
            return new Permit(null);
        }
        return s.tryAcquire() ? new Permit(s) : null;
    }

    public int available(ServiceName target) {
        Semaphore s = semaphoreOf(target);
        return s == null ? Integer.MAX_VALUE : s.availablePermits();
    }

    private Semaphore semaphoreOf(ServiceName target) {
        return switch (policy.mode()) {
            case SHARED -> shared;
            case PER_SERVICE -> perService.get(target);
            case UNLIMITED -> null;
        };
    }
}
