package com.springlab.lab.micro;

import com.springlab.lab.micro.core.Bulkheads;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.HandlerRegistry;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ServiceBehaviours;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceModule;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.ServiceResult;
import com.springlab.lab.micro.core.SimClock;
import com.springlab.lab.micro.core.Span;
import com.springlab.lab.micro.core.TraceBook;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The ServiceBus without a database: outcome semantics, timeouts on the logical clock, bulkheads, spans and correlation. */
class ServiceBusTest {

    static final Operation<String, String> ECHO = new Operation<>(ServiceName.PRODUCT, "echo");
    static final Operation<String, String> REFUSE = new Operation<>(ServiceName.PRODUCT, "refuse");
    static final Operation<String, String> BOOM = new Operation<>(ServiceName.PRODUCT, "boom");
    /** An Inventory operation that calls Product while it runs, so spans nest. */
    static final Operation<String, String> OUTER = new Operation<>(ServiceName.INVENTORY, "outer");

    final AtomicInteger productHandlerRuns = new AtomicInteger();

    /** The module for Product registers ECHO, REFUSE and BOOM; the module for Inventory registers OUTER. */
    HandlerRegistry registry() {
        ServiceModule product = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.PRODUCT;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(ECHO, (ctx, q) -> {
                    productHandlerRuns.incrementAndGet();
                    return ServiceResult.ok("echo:" + q);
                });
                r.on(REFUSE, (ctx, q) -> ServiceResult.failure("NOPE"));
                r.on(BOOM, (ctx, q) -> {
                    throw new IllegalStateException("secret detail that must not be recorded");
                });
            }
        };
        ServiceModule inventory = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.INVENTORY;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(OUTER, (ctx, q) -> {
                    CallResult<String> inner = ctx.bus().call(ServiceName.INVENTORY, ECHO, q);
                    return inner.isSuccess() ? ServiceResult.ok("outer:" + inner.value()) : ServiceResult.failure("INNER_" + inner.outcome());
                });
            }
        };
        return new HandlerRegistry(List.of(product, inventory));
    }

    record Fixture(ServiceBus bus, ServiceBehaviours behaviours, TraceBook traces, SimClock clock) {
    }

    Fixture fixture(Bulkheads.Policy policy, boolean propagate) {
        SimClock clock = new SimClock();
        ServiceBehaviours behaviours = new ServiceBehaviours();
        TraceBook traces = new TraceBook();
        ServiceBus bus = new ServiceBus(registry(), clock, behaviours, new Bulkheads(policy), traces, propagate);
        bus.startRequest();
        return new Fixture(bus, behaviours, traces, clock);
    }

    Fixture fixture() {
        return fixture(Bulkheads.Policy.perService(2), true);
    }

    // ---- outcomes

    @Test
    void aSuccessfulCallReturnsTheValueAndTheTargetsBaseLatency() {
        Fixture f = fixture();
        CallResult<String> r = f.bus.call(null, ECHO, "hi");
        assertThat(r.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(r.value()).isEqualTo("echo:hi");
        assertThat(r.handlerInvoked()).isTrue();
        assertThat(r.simulatedMillis()).isEqualTo(ServiceBus.baseLatency(ServiceName.PRODUCT));
        assertThat(f.clock.now()).isEqualTo(r.simulatedMillis());
    }

    @Test
    void aBusinessRefusalIsNotAFailureOfTheService() {
        CallResult<String> r = fixture().bus.call(null, REFUSE, "x");
        assertThat(r.outcome()).isEqualTo(Outcome.BUSINESS_FAILURE);
        assertThat(r.code()).isEqualTo("NOPE");
        assertThat(r.handlerInvoked()).isTrue();
    }

    @Test
    void anUnavailableServiceIsNeverInvoked() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.down());
        CallResult<String> r = f.bus.call(null, ECHO, "x");
        assertThat(r.outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(r.handlerInvoked()).isFalse();
        assertThat(productHandlerRuns).hasValue(0);
    }

    @Test
    void aLostRequestTimesOutWithoutBeingProcessed() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.requestLost());
        CallResult<String> r = f.bus.call(null, ECHO, "x", CallOptions.timeout(100));
        assertThat(r.outcome()).isEqualTo(Outcome.TIMEOUT);
        assertThat(r.code()).isEqualTo("REQUEST_LOST");
        assertThat(r.handlerInvoked()).isFalse();
        assertThat(r.simulatedMillis()).isEqualTo(100);
        assertThat(productHandlerRuns).hasValue(0);
    }

    @Test
    void aSlowResponseTimesOutEvenThoughTheWorkWasDone() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.delayed(500));
        CallResult<String> r = f.bus.call(null, ECHO, "x", CallOptions.timeout(100));
        assertThat(r.outcome()).isEqualTo(Outcome.TIMEOUT);
        assertThat(r.code()).isEqualTo("RESPONSE_TOO_SLOW");
        assertThat(r.value()).as("the caller never receives the value").isNull();
        assertThat(r.handlerInvoked()).as("but the lab can see the work was done").isTrue();
        assertThat(productHandlerRuns).hasValue(1);
        assertThat(r.simulatedMillis()).isEqualTo(100);
    }

    @Test
    void aDelayInsideTheTimeoutIsJustASlowSuccess() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.delayed(40));
        CallResult<String> r = f.bus.call(null, ECHO, "x", CallOptions.timeout(100));
        assertThat(r.outcome()).isEqualTo(Outcome.SUCCESS);
        assertThat(r.simulatedMillis()).isEqualTo(ServiceBus.baseLatency(ServiceName.PRODUCT) + 40);
    }

    @Test
    void anInjectedErrorIsATechnicalErrorBeforeAnyWork() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.error());
        CallResult<String> r = f.bus.call(null, ECHO, "x");
        assertThat(r.outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);
        assertThat(r.code()).isEqualTo("SimulatedFault");
        assertThat(r.handlerInvoked()).isFalse();
    }

    @Test
    void aRealHandlerExceptionIsATechnicalErrorAndOnlyTheClassIsRecorded() {
        Fixture f = fixture();
        CallResult<String> r = f.bus.call(null, BOOM, "x");
        assertThat(r.outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);
        assertThat(r.code()).isEqualTo("IllegalStateException");
        assertThat(r.handlerInvoked()).isTrue();
        assertThat(f.traces.calls().toString() + f.traces.spans()).doesNotContain("secret detail");
    }

    @Test
    void aTransientFaultAffectsOnlyTheFirstCalls() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.errorFirst(2));
        assertThat(f.bus.call(null, ECHO, "1").outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);
        assertThat(f.bus.call(null, ECHO, "2").outcome()).isEqualTo(Outcome.TECHNICAL_ERROR);
        assertThat(f.bus.call(null, ECHO, "3").outcome()).isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void behaviourIsPerRunAndNeverShared() {
        Fixture a = fixture();
        Fixture b = fixture();
        a.behaviours.set(ServiceName.PRODUCT, Behaviour.down());
        assertThat(a.bus.call(null, ECHO, "x").outcome()).isEqualTo(Outcome.UNAVAILABLE);
        assertThat(b.bus.call(null, ECHO, "x").outcome()).isEqualTo(Outcome.SUCCESS);
    }

    // ---- bulkhead

    @Test
    void aSaturatedServiceRejectsCallsWithoutReachingIt() {
        Fixture f = fixture(Bulkheads.Policy.perService(2), true);
        try (Bulkheads.Permit a = f.bus.holdPermit(ServiceName.PRODUCT); Bulkheads.Permit b = f.bus.holdPermit(ServiceName.PRODUCT)) {
            CallResult<String> r = f.bus.call(null, ECHO, "x");
            assertThat(r.outcome()).isEqualTo(Outcome.BULKHEAD_REJECTED);
            assertThat(r.handlerInvoked()).isFalse();
            assertThat(productHandlerRuns).hasValue(0);
        }
        assertThat(f.bus.call(null, ECHO, "x").outcome()).as("permits are returned when the held requests finish").isEqualTo(Outcome.SUCCESS);
    }

    @Test
    void withSeparatePermitsASaturatedServiceDoesNotStarveAnother() {
        Fixture f = fixture(Bulkheads.Policy.perService(2), true);
        try (Bulkheads.Permit a = f.bus.holdPermit(ServiceName.PRODUCT); Bulkheads.Permit b = f.bus.holdPermit(ServiceName.PRODUCT)) {
            assertThat(f.bus.bulkheads().available(ServiceName.INVENTORY)).isEqualTo(2);
            CallResult<String> other = f.bus.call(null, OUTER, "x");
            assertThat(other.outcome()).as("Inventory still works; its nested call to saturated Product is rejected")
                    .isEqualTo(Outcome.BUSINESS_FAILURE);
            assertThat(other.code()).isEqualTo("INNER_BULKHEAD_REJECTED");
        }
    }

    @Test
    void withOneSharedPoolSaturationStarvesEveryService() {
        Fixture f = fixture(Bulkheads.Policy.shared(2), true);
        try (Bulkheads.Permit a = f.bus.holdPermit(ServiceName.PRODUCT); Bulkheads.Permit b = f.bus.holdPermit(ServiceName.PRODUCT)) {
            CallResult<String> r = f.bus.call(null, OUTER, "x");
            assertThat(r.outcome()).as("Inventory is rejected although Inventory itself is idle").isEqualTo(Outcome.BULKHEAD_REJECTED);
        }
    }

    @Test
    void closingAPermitTwiceReleasesItOnce() {
        Bulkheads bulkheads = new Bulkheads(Bulkheads.Policy.perService(1));
        Bulkheads.Permit p = bulkheads.tryAcquire(ServiceName.PAYMENT);
        p.close();
        p.close();
        assertThat(bulkheads.available(ServiceName.PAYMENT)).isEqualTo(1);
        assertThat(bulkheads.tryAcquire(ServiceName.PAYMENT)).isNotNull();
        assertThat(bulkheads.tryAcquire(ServiceName.PAYMENT)).isNull();
    }

    @Test
    void aFailedCallAlwaysReturnsItsPermit() {
        Fixture f = fixture(Bulkheads.Policy.perService(1), true);
        f.bus.call(null, BOOM, "x");
        f.bus.call(null, REFUSE, "x");
        assertThat(f.bus.bulkheads().available(ServiceName.PRODUCT)).isEqualTo(1);
    }

    // ---- spans and correlation

    @Test
    void nestedCallsFormAParentChildTreeUnderOneCorrelationId() {
        Fixture f = fixture();
        f.bus.call(null, OUTER, "x");
        List<Span> spans = f.traces.spans();
        assertThat(spans).hasSize(2);
        Span child = spans.get(0); // children finish first
        Span parent = spans.get(1);
        assertThat(parent.parentSpanId()).isNull();
        assertThat(child.parentSpanId()).isEqualTo(parent.spanId());
        assertThat(child.correlationId()).isEqualTo(parent.correlationId());
        assertThat(parent.simulatedMillis()).as("a parent includes the time of its child")
                .isEqualTo(ServiceBus.baseLatency(ServiceName.INVENTORY) + child.simulatedMillis());
        assertThat(child.startMillis()).isGreaterThanOrEqualTo(parent.startMillis());
        assertThat(child.service()).isEqualTo("product");
        assertThat(parent.service()).isEqualTo("inventory");
    }

    @Test
    void eachExternalRequestGetsItsOwnCorrelationId() {
        Fixture f = fixture();
        f.bus.call(null, ECHO, "a");
        f.bus.startRequest();
        f.bus.call(null, ECHO, "b");
        List<Span> spans = f.traces.spans();
        assertThat(spans.get(0).correlationId()).isNotEqualTo(spans.get(1).correlationId());
        assertThat(spans.get(0).spanId()).isNotEqualTo(spans.get(1).spanId());
    }

    @Test
    void whenCorrelationIsNotPropagatedTheChainIsBroken() {
        Fixture f = fixture(Bulkheads.Policy.perService(2), false);
        f.bus.call(null, OUTER, "x");
        List<Span> spans = f.traces.spans();
        Span child = spans.get(0);
        Span parent = spans.get(1);
        assertThat(child.correlationPropagated()).isFalse();
        assertThat(child.parentSpanId()).as("the link to the parent is lost").isNull();
        assertThat(child.correlationId()).isNotEqualTo(parent.correlationId());
        assertThat(parent.correlationPropagated()).isTrue();
    }

    @Test
    void aTimeoutIsRecordedAsTheCallersTimeoutNotTheServicesLatency() {
        Fixture f = fixture();
        f.behaviours.set(ServiceName.PRODUCT, Behaviour.delayed(900));
        f.bus.call(null, ECHO, "x", CallOptions.timeout(120));
        Span span = f.traces.spans().get(0);
        assertThat(span.status()).isEqualTo("TIMEOUT");
        assertThat(span.simulatedMillis()).isEqualTo(120);
    }

    @Test
    void callRecordsNeverContainThePayload() {
        Fixture f = fixture();
        f.bus.call(null, ECHO, "TOP-SECRET-PAYLOAD");
        CallRecord record = f.traces.calls().get(0);
        assertThat(record.toString() + record.describe()).doesNotContain("TOP-SECRET-PAYLOAD");
        assertThat(record.from()).isEqualTo("client");
    }

    @Test
    void aRunCannotMakeMoreThanTheCallLimit() {
        Fixture f = fixture(Bulkheads.Policy.unlimited(), true);
        for (int i = 0; i < ServiceBus.MAX_CALLS; i++) {
            f.bus.call(null, ECHO, "x");
        }
        assertThatThrownBy(() -> f.bus.call(null, ECHO, "x")).isInstanceOf(IllegalStateException.class).hasMessageContaining("at most");
    }

    // ---- registry

    @Test
    void aServiceCannotImplementAnotherServicesOperation() {
        ServiceModule rogue = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.INVENTORY;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(ECHO, (ctx, q) -> ServiceResult.ok("hijacked")); // ECHO belongs to Product
            }
        };
        assertThatThrownBy(() -> new HandlerRegistry(List.of(rogue))).isInstanceOf(IllegalStateException.class).hasMessageContaining("belongs to product");
    }

    @Test
    void anOperationCannotHaveTwoHandlers() {
        ServiceModule one = new ServiceModule() {
            public ServiceName service() {
                return ServiceName.PRODUCT;
            }

            public void register(HandlerRegistry.Registrar r) {
                r.on(ECHO, (ctx, q) -> ServiceResult.ok("1"));
                r.on(ECHO, (ctx, q) -> ServiceResult.ok("2"));
            }
        };
        assertThatThrownBy(() -> new HandlerRegistry(List.of(one))).isInstanceOf(IllegalStateException.class).hasMessageContaining("two handlers");
    }

    @Test
    void callingAnUnregisteredOperationIsAProgrammingError() {
        Fixture f = fixture();
        Operation<String, String> missing = new Operation<>(ServiceName.PAYMENT, "nothing");
        assertThatThrownBy(() -> f.bus.call(null, missing, "x")).isInstanceOf(IllegalArgumentException.class);
    }
}
