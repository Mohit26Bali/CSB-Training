package com.springlab.lab;

import com.springlab.lab.trace.TraceTicketStore;
import com.springlab.support.MutableClock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class TraceTicketStoreTest {

    private static TraceTicketStore store(MutableClock clock, Duration ttl, int max) throws Exception {
        Constructor<TraceTicketStore> ctor = TraceTicketStore.class.getDeclaredConstructor(
                java.time.Clock.class, Duration.class, int.class);
        ctor.setAccessible(true);
        return ctor.newInstance(clock, ttl, max);
    }

    @Test
    void aTicketIsBoundToItsOwnerAndCanBeRedeemedOnce() throws Exception {
        TraceTicketStore store = store(new MutableClock(), Duration.ofSeconds(60), 10);
        String forAlice = store.issue(1L);
        String forBob = store.issue(2L);

        assertThat(forAlice).isNotEqualTo(forBob);
        assertThat(store.redeem(forAlice).getAsLong()).isEqualTo(1L);
        assertThat(store.redeem(forAlice)).isEmpty(); // spent
        assertThat(store.redeem(forBob).getAsLong()).isEqualTo(2L); // unaffected by Alice's redemption
    }

    @Test
    void unknownOrMalformedTicketsRedeemToNothing() throws Exception {
        TraceTicketStore store = store(new MutableClock(), Duration.ofSeconds(60), 10);
        store.issue(1L);

        for (String bogus : List.of("1", "true", "", " ", "garbage", UUID.randomUUID().toString())) {
            assertThat(store.redeem(bogus)).as(bogus).isEmpty();
        }
        assertThat(store.outstanding()).isEqualTo(1); // junk neither consumes nor creates anything
    }

    @Test
    void ticketsAreRandomAndUnguessable() throws Exception {
        TraceTicketStore store = store(new MutableClock(), Duration.ofSeconds(60), 1000);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String ticket = store.issue(7L);
            assertThat(UUID.fromString(ticket).version()).isEqualTo(4); // random (v4) UUID
            assertThat(seen.add(ticket)).isTrue();
        }
    }

    @Test
    void ticketsExpire() throws Exception {
        MutableClock clock = new MutableClock();
        TraceTicketStore store = store(clock, Duration.ofSeconds(60), 10);
        String early = store.issue(1L);
        clock.advance(Duration.ofSeconds(50));
        String late = store.issue(2L);

        clock.advance(Duration.ofSeconds(20)); // early is now 70 s old, late is 20 s old
        assertThat(store.redeem(early)).isEmpty();
        assertThat(store.redeem(late).getAsLong()).isEqualTo(2L);
    }

    @Test
    void outstandingTicketsAreBoundedAndTheOldestIsDroppedFirst() throws Exception {
        TraceTicketStore store = store(new MutableClock(), Duration.ofSeconds(60), 3);
        List<String> tickets = List.of(store.issue(1L), store.issue(2L), store.issue(3L), store.issue(4L), store.issue(5L));

        assertThat(store.outstanding()).isEqualTo(3);
        assertThat(store.redeem(tickets.get(0))).isEmpty();
        assertThat(store.redeem(tickets.get(1))).isEmpty();
        assertThat(store.redeem(tickets.get(4)).getAsLong()).isEqualTo(5L);
    }

    @Test
    void concurrentRedemptionsOfOneTicketHaveExactlyOneWinner() throws Exception {
        TraceTicketStore store = store(new MutableClock(), Duration.ofSeconds(60), 10);
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            for (int round = 0; round < 20; round++) {
                String ticket = store.issue(42L);
                CyclicBarrier together = new CyclicBarrier(8);
                AtomicInteger winners = new AtomicInteger();
                List<Callable<Void>> tasks = new java.util.ArrayList<>();
                for (int i = 0; i < 8; i++) {
                    tasks.add(() -> {
                        together.await(5, TimeUnit.SECONDS);
                        if (store.redeem(ticket).isPresent()) {
                            winners.incrementAndGet();
                        }
                        return null;
                    });
                }
                for (Future<Void> f : pool.invokeAll(tasks, 20, TimeUnit.SECONDS)) {
                    f.get();
                }
                assertThat(winners.get()).as("round %d", round).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
