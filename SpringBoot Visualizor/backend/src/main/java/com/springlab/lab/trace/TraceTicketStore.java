package com.springlab.lab.trace;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Binds a traced request to the authenticated user who asked for it.
 *
 * <p>A traced request can itself be anonymous or carry a deliberately bad token (that is the point of several
 * experiments), so its own authentication cannot say who owns the resulting trace. Instead, an authenticated user first
 * obtains a ticket here, and sends it in the trace header. A ticket is random, bound to that user's database id,
 * single-use, short-lived, and the number outstanding is bounded. Nobody can create a trace without one, so every stored
 * trace has an owner.
 */
@Component
public class TraceTicketStore {

    static final Duration DEFAULT_TTL = Duration.ofSeconds(60);
    static final int DEFAULT_MAX_OUTSTANDING = 500;

    private record Ticket(long ownerId, Instant issuedAt) {
    }

    private final Clock clock;
    private final Duration ttl;
    private final int maxOutstanding;
    private final Map<String, Ticket> tickets = new LinkedHashMap<>();

    @Autowired
    public TraceTicketStore() {
        this(Clock.systemUTC(), DEFAULT_TTL, DEFAULT_MAX_OUTSTANDING);
    }

    TraceTicketStore(Clock clock, Duration ttl, int maxOutstanding) {
        this.clock = clock;
        this.ttl = ttl;
        this.maxOutstanding = maxOutstanding;
    }

    public Duration timeToLive() {
        return ttl;
    }

    public synchronized String issue(long ownerId) {
        evictExpired();
        String ticket = UUID.randomUUID().toString(); // 122 random bits from a SecureRandom
        tickets.put(ticket, new Ticket(ownerId, clock.instant()));
        while (tickets.size() > maxOutstanding) { // oldest first
            Iterator<String> oldest = tickets.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        return ticket;
    }

    /** Atomically consumes the ticket: the first caller gets the owner id, everyone else (and later calls) get nothing. */
    public synchronized OptionalLong redeem(String ticket) {
        evictExpired();
        Ticket found = tickets.remove(ticket);
        return found == null ? OptionalLong.empty() : OptionalLong.of(found.ownerId());
    }

    /** Number of unexpired, unredeemed tickets (a count only; never exposes a ticket). */
    public synchronized int outstanding() {
        evictExpired();
        return tickets.size();
    }

    private void evictExpired() {
        Instant cutoff = clock.instant().minus(ttl);
        tickets.values().removeIf(t -> t.issuedAt().isBefore(cutoff));
    }
}
