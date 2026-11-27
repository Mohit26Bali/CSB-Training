package com.springlab.lab.trace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Short-lived, size-bounded, in-memory store of assembled traces. Every trace belongs to the user (stable database id)
 * who obtained the ticket for the traced request, and can only be read back by that user: a request for someone else's
 * trace is indistinguishable from a request for a trace that does not exist.
 */
@Component
public class TraceStore {

    static final int DEFAULT_MAX_TRACES = 200;
    static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final Clock clock;
    private final int maxTraces;
    private final Duration ttl;
    private final Map<String, Stored> traces = new LinkedHashMap<>();

    private record Stored(LifecycleTrace trace, long ownerId, Instant storedAt) {
    }

    @Autowired
    public TraceStore() {
        this(Clock.systemUTC(), DEFAULT_MAX_TRACES, DEFAULT_TTL);
    }

    TraceStore(Clock clock, int maxTraces, Duration ttl) {
        this.clock = clock;
        this.maxTraces = maxTraces;
        this.ttl = ttl;
    }

    public synchronized void put(LifecycleTrace trace, long ownerId) {
        evictExpired();
        traces.put(trace.id(), new Stored(trace, ownerId, clock.instant()));
        while (traces.size() > maxTraces) { // oldest first (insertion order), whoever owns it
            Iterator<String> oldest = traces.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
    }

    /** Empty when the id is unknown, expired, OR belongs to a different user. */
    public synchronized Optional<LifecycleTrace> findOwned(String id, long requesterId) {
        evictExpired();
        Stored stored = traces.get(id);
        if (stored == null || stored.ownerId() != requesterId) {
            return Optional.empty();
        }
        return Optional.of(stored.trace());
    }

    public synchronized int size() {
        evictExpired();
        return traces.size();
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        traces.values().removeIf(stored -> stored.storedAt().isBefore(cutoff));
    }
}
