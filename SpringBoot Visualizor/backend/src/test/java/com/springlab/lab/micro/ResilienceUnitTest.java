package com.springlab.lab.micro;

import com.springlab.lab.micro.contract.RegistryContract;
import com.springlab.lab.micro.core.Bulkheads;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.CircuitBreaker;
import com.springlab.lab.micro.core.CircuitBreaker.State;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ResilientCaller;
import com.springlab.lab.micro.core.RetryPolicy;
import com.springlab.lab.micro.core.ServiceBehaviours;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.core.SimClock;
import com.springlab.lab.micro.core.TraceBook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The deterministic resilience building blocks, without a database. */
class ResilienceUnitTest {

    static final Operation<String, String> WORK = new Operation<>(ServiceName.PRODUCT, "work");
    static final Operation<String, String> REFUSE = new Operation<>(ServiceName.PRODUCT, "refuse");

    final SimClock clock = new SimClock();
    final ServiceBehaviours behaviours = new ServiceBehaviours();
    final TraceBook traces = new TraceBook();
    final AtomicInteger handlerRuns = new AtomicInteger();
    final ServiceBus bus;

    ResilienceUnitTest() {
        ServiceModule product = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.PRODUCT;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(WORK, (ctx, q) -> {
                    handlerRuns.incrementAndGet();
                    return ServiceResult.ok("done");
                });
                r.on(REFUSE, (ctx, q) -> ServiceResult.failure("NO"));
            }
        };
        bus = new ServiceBus(new HandlerRegistry(List.of(product)), clock, behaviours, new Bulkheads(Bulkheads.Policy.perService(2)), traces, true);
        bus.startRequest();
    }

    // ---- circuit breaker

    @Test
    void theBreakerOpensAfterTheThresholdOfConsecutiveFailuresAndNotBefore() {
        CircuitBreaker b = new CircuitBreaker(3, 1000, clock);
        b.recordFailure();
        b.recordFailure();
        assertThat(b.state()).isEqualTo(State.CLOSED);
        b.recordFailure();
        assertThat(b.state()).isEqualTo(State.OPEN);
        assertThat(b.allowRequest()).isFalse();
    }

    @Test
    void aSuccessResetsTheConsecutiveFailureCount() {
        CircuitBreaker b = new CircuitBreaker(3, 1000, clock);
        b.recordFailure();
        b.recordFailure();
        b.recordSuccess();
        b.recordFailure();
        b.recordFailure();
        assertThat(b.state()).as("two + success + two is never three in a row").isEqualTo(State.CLOSED);
    }

    @Test
    void theOpenPeriodEndsExactlyAtTheConfiguredDuration() {
        CircuitBreaker b = new CircuitBreaker(1, 1000, clock);
        b.recordFailure(); // opens at t = 0
        clock.advanceTo(999);
        assertThat(b.allowRequest()).as("999 ms: still open").isFalse();
        assertThat(b.state()).isEqualTo(State.OPEN);
        clock.advanceTo(1000);
        assertThat(b.allowRequest()).as("exactly 1000 ms: the probe is allowed").isTrue();
        assertThat(b.state()).isEqualTo(State.HALF_OPEN);
    }

    @Test
    void halfOpenAllowsExactlyOneProbeAtATime() {
        CircuitBreaker b = new CircuitBreaker(1, 100, clock);
        b.recordFailure();
        clock.advanceTo(100);
        assertThat(b.allowRequest()).isTrue();
        assertThat(b.allowRequest()).as("the probe is in flight").isFalse();
    }

    @Test
    void aSuccessfulProbeClosesTheBreakerAndAFailedProbeReopensItWithAFreshTimer() {
        CircuitBreaker ok = new CircuitBreaker(1, 100, clock);
        ok.recordFailure();
        clock.advanceTo(100);
        ok.allowRequest();
        ok.recordSuccess();
        assertThat(ok.state()).isEqualTo(State.CLOSED);

        CircuitBreaker bad = new CircuitBreaker(1, 100, clock);
        bad.recordFailure(); // opens at t = 100 (clock already at 100)
        clock.advanceTo(200);
        bad.allowRequest();
        bad.recordFailure();
        assertThat(bad.state()).isEqualTo(State.OPEN);
        clock.advanceTo(299);
        assertThat(bad.allowRequest()).as("the timer restarted at the failed probe").isFalse();
        clock.advanceTo(300);
        assertThat(bad.allowRequest()).isTrue();
    }

    @Test
    void everyStateChangeIsRecordedWithItsTimeAndReason() {
        CircuitBreaker b = new CircuitBreaker(2, 50, clock);
        b.recordFailure();
        b.recordFailure();
        clock.advanceTo(60);
        b.allowRequest();
        b.recordSuccess();
        assertThat(b.transitions()).extracting(t -> t.from() + ">" + t.to()).containsExactly("CLOSED>OPEN", "OPEN>HALF_OPEN", "HALF_OPEN>CLOSED");
        assertThat(b.transitions().get(0).describe()).contains("2 consecutive failures");
    }

    @Test
    void thresholdAndDurationMustBePositive() {
        assertThatThrownBy(() -> new CircuitBreaker(0, 10, clock)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CircuitBreaker(1, 0, clock)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- retry policy

    @Test
    void backoffDoublesAndIsCappedWithNoRandomness() {
        RetryPolicy p = new RetryPolicy(6, 100, 800);
        assertThat(List.of(p.backoffBefore(1), p.backoffBefore(2), p.backoffBefore(3), p.backoffBefore(4), p.backoffBefore(5))).containsExactly(100, 200, 400, 800, 800);
        assertThat(p.backoffBefore(3)).isEqualTo(new RetryPolicy(6, 100, 800).backoffBefore(3));
    }

    @Test
    void attemptsAreBounded() {
        assertThatThrownBy(() -> new RetryPolicy(0, 100, 800)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RetryPolicy(11, 100, 800)).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- ResilientCaller

    @Test
    void aTransientFailureIsOvercomeAndTheWaitsAreOnTheSimulatedClock() {
        behaviours.set(ServiceName.PRODUCT, Behaviour.errorFirst(2));
        ResilientCaller.Result<String> r = ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), new RetryPolicy(4, 100, 800), null, null, true, null);
        assertThat(r.attempts()).extracting(a -> a.outcome().name()).containsExactly("TECHNICAL_ERROR", "TECHNICAL_ERROR", "SUCCESS");
        assertThat(r.attempts()).extracting(a -> a.backoffBeforeMillis()).containsExactly(0, 100, 200);
        assertThat(r.succeeded()).isTrue();
        assertThat(clock.now()).as("three calls of 15 ms plus waits of 100 and 200").isEqualTo(3 * 15 + 300);
    }

    @Test
    void retriesStopAtTheLimit() {
        behaviours.set(ServiceName.PRODUCT, Behaviour.error());
        ResilientCaller.Result<String> r = ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.of(3), null, null, true, null);
        assertThat(r.attempts()).hasSize(3);
        assertThat(r.succeeded()).isFalse();
    }

    @Test
    void aBusinessRefusalIsNeverRetried() {
        ResilientCaller.Result<String> r = ResilientCaller.call(bus, null, REFUSE, "x", CallOptions.defaults(), RetryPolicy.of(5), null, null, true, null);
        assertThat(r.attempts()).hasSize(1);
        assertThat(r.last().outcome()).isEqualTo(Outcome.BUSINESS_FAILURE);
    }

    @Test
    void aTimeoutIsRetriedOnlyWhenTheCallerSaysTheOperationIsIdempotent() {
        behaviours.set(ServiceName.PRODUCT, Behaviour.delayed(500));
        CallOptions tight = CallOptions.timeout(100);
        assertThat(ResilientCaller.call(bus, null, WORK, "x", tight, RetryPolicy.of(3), null, null, false, null).attempts()).hasSize(1);
        assertThat(ResilientCaller.call(bus, null, WORK, "x", tight, RetryPolicy.of(3), null, null, true, null).attempts()).hasSize(3);
        assertThat(handlerRuns.get()).as("every timed-out attempt really ran the handler").isEqualTo(4);
    }

    @Test
    void anOpenBreakerRefusesWithoutCallingAndIsNotRetriedButIsRecordedInTheTrace() {
        CircuitBreaker breaker = new CircuitBreaker(1, 10_000, clock);
        behaviours.set(ServiceName.PRODUCT, Behaviour.error());
        ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.none(), breaker, null, false, null);
        assertThat(breaker.state()).isEqualTo(State.OPEN);
        int callsBefore = traces.callCount();

        ResilientCaller.Result<String> r = ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.of(5), breaker, null, true, null);
        assertThat(r.attempts()).hasSize(1);
        assertThat(r.last().outcome()).isEqualTo(Outcome.CIRCUIT_OPEN);
        assertThat(r.last().handlerInvoked()).isFalse();
        assertThat(traces.callCount()).as("the refusal is in the trace").isEqualTo(callsBefore + 1);
        assertThat(traces.spans().get(traces.spans().size() - 1).status()).isEqualTo("CIRCUIT_OPEN");
    }

    @Test
    void aBusinessRefusalCountsAsHealthyForTheBreaker() {
        CircuitBreaker breaker = new CircuitBreaker(2, 1000, clock);
        behaviours.set(ServiceName.PRODUCT, Behaviour.error());
        ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.none(), breaker, null, false, null);
        behaviours.reset(ServiceName.PRODUCT);
        ResilientCaller.call(bus, null, REFUSE, "x", CallOptions.defaults(), RetryPolicy.none(), breaker, null, false, null);
        behaviours.set(ServiceName.PRODUCT, Behaviour.error());
        ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.none(), breaker, null, false, null);
        assertThat(breaker.state()).as("fail, refuse (healthy), fail: never two failures in a row").isEqualTo(State.CLOSED);
    }

    @Test
    void theFallbackAnswersOnlyWhenTheCallFailed() {
        ResilientCaller.Result<String> healthy = ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.none(), null, () -> "stale", false, null);
        assertThat(healthy.fallbackUsed()).isFalse();
        behaviours.set(ServiceName.PRODUCT, Behaviour.down());
        ResilientCaller.Result<String> failed = ResilientCaller.call(bus, null, WORK, "x", CallOptions.defaults(), RetryPolicy.none(), null, () -> "stale", false, null);
        assertThat(failed.fallbackUsed()).isTrue();
        assertThat(failed.fallbackValue()).isEqualTo("stale");
        assertThat(failed.last().outcome()).as("the failure is still reported honestly").isEqualTo(Outcome.UNAVAILABLE);
    }

    @Test
    void waitingIsCountedAsTimeSpentByTheEnclosingSpan() {
        ServiceModule outer = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.ORDER;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(new Operation<String, String>(ServiceName.ORDER, "retrying"), (ctx, q) -> {
                    ctx.bus().pause(250);
                    return ServiceResult.ok("ok");
                });
            }
        };
        ServiceBus b = new ServiceBus(new HandlerRegistry(List.of(outer)), new SimClock(), new ServiceBehaviours(), new Bulkheads(Bulkheads.Policy.unlimited()), new TraceBook(), true);
        b.startRequest();
        CallResult<String> r = b.call(null, new Operation<String, String>(ServiceName.ORDER, "retrying"), "x", CallOptions.timeout(1000));
        assertThat(r.simulatedMillis()).isEqualTo(ServiceBus.baseLatency(ServiceName.ORDER) + 250);
    }

    // ---- delayed failure ("fail from the Nth call") used by the saga

    @Test
    void downAfterLetsTheFirstCallsThroughAndThenFails() {
        behaviours.set(ServiceName.PRODUCT, Behaviour.downAfter(1));
        assertThat(bus.call(null, WORK, "x").outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(bus.call(null, WORK, "x").outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(bus.call(null, WORK, "x").outcome()).isEqualTo(Outcome.UNAVAILABLE);
    }

    // ---- the registry expiry rule

    @Test
    void anInstanceIsFreshUpToAndIncludingTheTtlAndStaleOneMillisecondLater() {
        assertThat(RegistryContract.isFresh(0)).isTrue();
        assertThat(RegistryContract.isFresh(RegistryContract.TTL_MILLIS - 1)).isTrue();
        assertThat(RegistryContract.isFresh(RegistryContract.TTL_MILLIS)).as("exactly the TTL is still fresh").isTrue();
        assertThat(RegistryContract.isFresh(RegistryContract.TTL_MILLIS + 1)).as("TTL + 1 ms is stale").isFalse();
    }
}
