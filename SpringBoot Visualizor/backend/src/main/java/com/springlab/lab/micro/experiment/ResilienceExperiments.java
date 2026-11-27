package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.InventoryContract.Reservation;
import com.springlab.lab.micro.contract.InventoryContract.Reserve;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.OrderView;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.contract.PaymentContract;
import com.springlab.lab.micro.contract.PaymentContract.Charge;
import com.springlab.lab.micro.contract.PaymentContract.PaymentView;
import com.springlab.lab.micro.contract.ProductContract;
import com.springlab.lab.micro.contract.ProductContract.GetProduct;
import com.springlab.lab.micro.contract.ProductContract.ProductView;
import com.springlab.lab.micro.contract.UserContract;
import com.springlab.lab.micro.contract.UserContract.GetUser;
import com.springlab.lab.micro.core.Bulkheads;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.CircuitBreaker;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ResilientCaller;
import com.springlab.lab.micro.core.RetryPolicy;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRecorder;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.micro.run.WhatIfOption;
import com.springlab.lab.trace.CodeRefIds;
import com.springlab.lab.trace.LifecycleTrace.Evidence;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Topic 7: resilience. The retry, backoff, circuit breaker, fallback and idempotency behaviour is SpringLab's own small deterministic code
 * driven by the simulated clock (no sleeping, no randomness, no jitter). It is not Resilience4j. The bulkhead is a real set of Semaphores.
 */
public final class ResilienceExperiments {

    static final String OWN_CODE = "SpringLab's own deterministic state machine on the simulated clock (not Resilience4j)";

    private ResilienceExperiments() {
    }

    private static long callsTo(RunContext ctx, int mark, String service) {
        return ctx.traces().callsSince(mark).stream().filter(c -> c.to().equals(service) && c.outcome() != Outcome.CIRCUIT_OPEN).count();
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class RetryBackoff implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("res-retry-backoff", "resilience", "Retry with bounded attempts and deterministic backoff",
                    "How many times is a failing call tried, how long is the wait between tries, and which failures are never retried?",
                    "Retries are bounded and wait longer each time. A transient failure is overcome; a business refusal is never retried; a timeout is retried only when the "
                            + "operation is safe to repeat.",
                    "services", List.of(WhatIfOption.integer("maxAttempts", "Attempts allowed (including the first)", 3, 1, 5),
                            WhatIfOption.integer("transientFailures", "Calls that fail before the service recovers", 2, 0, 4)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int max = ctx.number("maxAttempts");
            int failures = ctx.number("transientFailures");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            RetryPolicy policy = new RetryPolicy(max, 100, 800);
            long[] ref = new long[2];

            rec.live("seed", "Fixtures: a customer and a product with 30 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "The retry policy is " + OWN_CODE + ": at most " + max + " attempts, waiting 100 ms, 200 ms, 400 ms (capped at 800) on the simulated clock.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 30);
                        return List.of(ev("Retry implementation", OWN_CODE));
                    });

            rec.live("transient", "A transient failure: " + failures + " failing call(s), then the service recovers", nodes("client", "inventory", "inventory-db"), "client", "inventory",
                    "Reserving is idempotent per order, so retrying it is safe. Each attempt is a real call; the wait between attempts is simulated time.", CodeRefIds.MICRO_RETRY, () -> {
                        if (failures > 0) {
                            ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.errorFirst(failures));
                        }
                        long start = bus.clock().now();
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        ResilientCaller.Result<Reservation> r = ResilientCaller.call(bus, null, InventoryContract.RESERVE, new Reserve(1, ref[1], 1), Lab.SETUP, policy, null, null, true, null);
                        List<Evidence> out = new ArrayList<>();
                        for (ResilientCaller.Attempt a : r.attempts()) {
                            out.add(ev("Attempt " + a.number(), a.describe()));
                        }
                        out.add(ev("Final outcome", Lab.outcome(r.last())));
                        out.add(ev("Calls that reached Inventory", callsTo(ctx, mark, "inventory")));
                        out.add(ev("Time spent, including waits (simulated)", (bus.clock().now() - start) + " ms"));
                        ctx.behaviours().reset(ServiceName.INVENTORY);
                        return out;
                    });

            rec.live("business-refusal", "A business refusal is never retried", nodes("client", "inventory"), "client", "inventory",
                    "Asking for more stock than exists gets OUT_OF_STOCK. Asking again would get the same answer, so the caller gives up at once.", CodeRefIds.MICRO_RETRY, () -> {
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        ResilientCaller.Result<Reservation> r = ResilientCaller.call(bus, null, InventoryContract.RESERVE, new Reserve(2, ref[1], 999), Lab.SETUP, policy, null, null, true, null);
                        return List.of(ev("Final outcome", Lab.outcome(r.last())), ev("Attempts made", r.attempts().size()), ev("Calls that reached Inventory", callsTo(ctx, mark, "inventory")));
                    });

            rec.live("timeout-rule", "A timeout is retried only when repeating is safe", nodes("client", "payment", "payment-db"), "client", "payment",
                    "Payment answers too slowly, so every call times out although the charge is made. Without an idempotency flag the caller does not retry. With the flag "
                            + "it retries, and the idempotency KEY makes the repeated charge harmless.", CodeRefIds.MICRO_RETRY, () -> {
                        ctx.behaviours().set(ServiceName.PAYMENT, Behaviour.delayed(400));
                        CallOptions options = CallOptions.timeout(100);
                        bus.startRequest();
                        ResilientCaller.Result<PaymentView> cautious = ResilientCaller.call(bus, null, PaymentContract.CHARGE,
                                new Charge(101, new BigDecimal("9.00"), "key-101"), options, policy, null, null, false, null);
                        bus.startRequest();
                        ResilientCaller.Result<PaymentView> keyed = ResilientCaller.call(bus, null, PaymentContract.CHARGE,
                                new Charge(102, new BigDecimal("9.00"), "key-102"), options, policy, null, null, true, null);
                        ctx.behaviours().reset(ServiceName.PAYMENT);
                        return List.of(ev("Without the idempotent flag: attempts", cautious.attempts().size()), ev("With the flag and a key: attempts", keyed.attempts().size()),
                                ev("Both end with", Lab.outcome(keyed.last())),
                                ev("Charges for the first order " + Lab.INSPECTOR_NOTE, ctx.inspectLong("select count(*) from micro_lab_payments where run_key = ? and order_ref = ?", 101)),
                                ev("Charges for the retried order " + Lab.INSPECTOR_NOTE, ctx.inspectLong("select count(*) from micro_lab_payments where run_key = ? and order_ref = ?", 102)));
                    });

            List<Evidence> schedule = new ArrayList<>();
            for (int retry = 1; retry < max; retry++) {
                schedule.add(ev("Wait before retry " + retry, policy.backoffBefore(retry) + " ms (simulated)"));
            }
            schedule.add(ev("Randomness or jitter", "none: the schedule is the same on every run"));
            rec.infer("backoff-schedule", "The backoff schedule", nodes("client", "inventory"),
                    "Calculated from the policy: the wait doubles each time and is capped. There is no sleeping and no randomness.", schedule);

            rec.concept("why-bounded", "Why retries must be bounded", nodes("client", "inventory"),
                    "Unbounded or immediate retries turn a short outage into a flood. Backoff gives the failing service room to recover, and a limit stops one caller from "
                            + "retrying for ever. Real systems usually add random jitter so that many clients do not retry in step; this lab leaves it out to stay deterministic.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class CircuitBreakerExperiment implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("res-circuit-breaker", "resilience", "Circuit breaker: CLOSED, OPEN, HALF_OPEN, and a fallback",
                    "What does a caller do after a dependency has failed several times in a row, and how does it find out the dependency is back?",
                    "After enough consecutive failures the breaker opens and calls are refused locally, without touching the failing service. After a wait one probe call is allowed: "
                            + "success closes the breaker, failure opens it again. A fallback keeps the caller answering meanwhile.",
                    "services", List.of(WhatIfOption.integer("failureThreshold", "Consecutive failures that open the breaker", 3, 2, 5),
                            WhatIfOption.integer("openMillis", "How long it stays open (simulated ms)", 2000, 500, 5000)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int threshold = ctx.number("failureThreshold");
            int openMillis = ctx.number("openMillis");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            CircuitBreaker breaker = new CircuitBreaker(threshold, openMillis, bus.clock());
            long[] ref = new long[2];
            ProductView[] cache = new ProductView[1];
            long[] spared = new long[1];

            java.util.function.Supplier<ResilientCaller.Result<ProductView>> callProduct = () -> {
                bus.startRequest();
                ResilientCaller.Result<ProductView> r = ResilientCaller.call(bus, null, ProductContract.GET_PRODUCT, new GetProduct(ref[1]), CallOptions.timeout(200),
                        RetryPolicy.none(), breaker, () -> cache[0], false, null);
                if (r.succeeded()) {
                    cache[0] = r.last().value();
                }
                return r;
            };

            rec.live("seed", "Fixtures: a product, and the breaker", nodes("client", "user", "product", "inventory"), "client", "product",
                    "The caller protects its calls to the Product service with a breaker (" + OWN_CODE + "). It opens after " + threshold + " consecutive failures and "
                            + "stays open for " + openMillis + " simulated ms. The fallback is the last price the caller saw.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Breaker implementation", OWN_CODE), ev("State", breaker.state()));
                    });

            rec.live("closed", "CLOSED: calls go through", nodes("client", "product", "product-db"), "client", "product",
                    "Healthy calls reach the Product service and the caller remembers the answer for the fallback.", CodeRefIds.MICRO_CIRCUIT_BREAKER, () -> {
                        ResilientCaller.Result<ProductView> a = callProduct.get();
                        ResilientCaller.Result<ProductView> b = callProduct.get();
                        return List.of(ev("Call 1", Lab.outcome(a.last())), ev("Call 2", Lab.outcome(b.last())), ev("Breaker state", breaker.state()),
                                ev("Last price remembered for the fallback", cache[0].price().toPlainString()));
                    });

            rec.live("failures-open-it", "The Product service starts failing: the breaker opens", nodes("client", "product"), "client", "product",
                    "Each failure is a real failed call and is counted. When the count reaches the threshold the breaker opens. Meanwhile the fallback answers.",
                    CodeRefIds.MICRO_CIRCUIT_BREAKER, () -> {
                        ctx.behaviours().set(ServiceName.PRODUCT, Behaviour.error());
                        List<Evidence> out = new ArrayList<>();
                        for (int i = 1; i <= threshold + 2 && breaker.state() != CircuitBreaker.State.OPEN; i++) {
                            ResilientCaller.Result<ProductView> r = callProduct.get();
                            out.add(ev("Call " + i, Lab.outcome(r.last()) + ", answered from " + (r.fallbackUsed() ? "the fallback (stale)" : "the service")
                                    + "; breaker " + breaker.state() + ", consecutive failures " + breaker.consecutiveFailures()));
                        }
                        out.add(ev("Breaker state", breaker.state()));
                        return out;
                    });

            rec.live("open", "OPEN: calls are refused locally", nodes("client", "product"), "client", "product",
                    "The breaker no longer lets calls through, so the failing service gets no traffic while it recovers, and the caller does not wait for it.",
                    CodeRefIds.MICRO_CIRCUIT_BREAKER, () -> {
                        int mark = ctx.traces().callCount();
                        List<Evidence> out = new ArrayList<>();
                        for (int i = 1; i <= 3; i++) {
                            ResilientCaller.Result<ProductView> r = callProduct.get();
                            out.add(ev("Call " + i, Lab.outcome(r.last()) + ", answered from " + (r.fallbackUsed() ? "the fallback (stale)" : "the service")));
                        }
                        spared[0] = ctx.traces().callsSince(mark).stream().filter(c -> c.outcome() == Outcome.CIRCUIT_OPEN).count();
                        out.add(ev("Calls that reached the Product service", callsTo(ctx, mark, "product")));
                        out.add(ev("Calls refused by the breaker", spared[0]));
                        out.add(ev("Breaker state", breaker.state()));
                        return out;
                    });

            rec.live("half-open-recovers", "HALF_OPEN: one probe call, and the service is back", nodes("client", "product"), "client", "product",
                    "After the open period the next call is let through as a probe. The Product service has recovered, the probe succeeds and the breaker closes.",
                    CodeRefIds.MICRO_CIRCUIT_BREAKER, () -> {
                        ctx.behaviours().reset(ServiceName.PRODUCT);
                        bus.pause(openMillis);
                        ResilientCaller.Result<ProductView> r = callProduct.get();
                        return List.of(ev("Waited (simulated)", openMillis + " ms"), ev("Probe", Lab.outcome(r.last())), ev("Answered from", r.fallbackUsed() ? "the fallback" : "the service (fresh)"),
                                ev("Breaker state", breaker.state()));
                    });

            rec.live("half-open-fails", "HALF_OPEN: the probe fails and the breaker opens again", nodes("client", "product"), "client", "product",
                    "The service fails again. The breaker opens, waits, and the probe fails too, so it opens again and the timer restarts.", CodeRefIds.MICRO_CIRCUIT_BREAKER, () -> {
                        ctx.behaviours().set(ServiceName.PRODUCT, Behaviour.error());
                        for (int i = 0; i < threshold + 2 && breaker.state() != CircuitBreaker.State.OPEN; i++) {
                            callProduct.get();
                        }
                        bus.pause(openMillis);
                        ResilientCaller.Result<ProductView> probe = callProduct.get();
                        return List.of(ev("Probe", Lab.outcome(probe.last())), ev("Breaker state", breaker.state()));
                    });

            List<Evidence> transitions = new ArrayList<>();
            int n = 1;
            for (CircuitBreaker.Transition t : breaker.transitions()) {
                transitions.add(ev("Transition " + n++, t.describe()));
            }
            transitions.add(ev("Calls spared from the failing service while OPEN (first open period)", spared[0]));
            rec.infer("transitions", "Every state change, in order", nodes("client", "product"),
                    "The list is the breaker's own record of its transitions, with the simulated time of each.", transitions);

            rec.concept("tuning", "Choosing the numbers", nodes("client", "product"),
                    "A low threshold or a short open period reacts quickly but trips on noise; a high one protects late. Only technical failures and timeouts count: a "
                            + "business refusal means the service is healthy. A fallback must be an honest degradation (here, a stale price) that the business accepts.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class IdempotencyKey implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("res-idempotency-key", "resilience", "Idempotency keys: a retry must not charge twice",
                    "A payment times out although the money was taken. The client retries. Is the customer charged twice?",
                    "With an idempotency key the Payment service recognises the retry and returns the original payment. Without one, the retry charges again.",
                    "services", List.of(WhatIfOption.bool("useKey", "The client sends an idempotency key", true)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean useKey = ctx.flag("useKey");
            String key = useKey ? "pay-order-7" : null;
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            boolean[] replayed = new boolean[1];

            rec.live("first-attempt", "The first charge times out, but the charge happened", nodes("client", "payment", "payment-db"), "client", "payment",
                    "Payment commits the charge and then answers too slowly. The client sees a timeout and cannot know whether it was charged.", CodeRefIds.MICRO_IDEMPOTENT_CHARGE, () -> {
                        ctx.behaviours().set(ServiceName.PAYMENT, Behaviour.delayed(400));
                        bus.startRequest();
                        CallResult<PaymentView> r = bus.call(null, PaymentContract.CHARGE, new Charge(7, new BigDecimal("9.00"), key), CallOptions.timeout(100));
                        ctx.behaviours().reset(ServiceName.PAYMENT);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Idempotency key sent", useKey ? "yes" : "no"),
                                ev("Payments CHARGED " + Lab.INSPECTOR_NOTE, Lab.payments(ctx, "CHARGED")));
                    });

            rec.live("retry", "The client retries the same request", nodes("client", "payment", "payment-db"), "client", "payment",
                    "Payment is healthy again. " + (useKey ? "It finds the key it already stored and returns the original payment without charging."
                            : "It has no way to recognise the retry, so it charges again."), CodeRefIds.MICRO_IDEMPOTENT_CHARGE, () -> {
                        bus.startRequest();
                        CallResult<PaymentView> r = bus.call(null, PaymentContract.CHARGE, new Charge(7, new BigDecimal("9.00"), key), CallOptions.timeout(500));
                        replayed[0] = r.isSuccess() && r.value().replayed();
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Answer was a replay of the first payment", replayed[0]),
                                ev("Payments CHARGED " + Lab.INSPECTOR_NOTE, Lab.payments(ctx, "CHARGED")));
                    });

            long charged = Lab.payments(ctx, "CHARGED");
            rec.infer("outcome", "How many times was the customer charged?", nodes("payment"),
                    "Calculated from the payment rows that the Payment service holds.",
                    List.of(ev("Times the customer was charged", charged), ev("Protected by the idempotency key", charged == 1 && useKey),
                            ev("Why", useKey ? "the key is unique in the Payment service's own table, so the second request found the first" : "without a key the Payment service cannot tell a retry from a new request")));

            rec.concept("keys-in-practice", "Where keys come from", nodes("client", "payment"),
                    "The client generates the key once per intended operation (not per attempt) and sends the same key on every retry. The service stores it with the result, "
                            + "enforces uniqueness in the database and keeps it long enough to cover the retry window. A key does not make a different request safe: the same key "
                            + "with different content should be rejected in a production service.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class RetryAmplification implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("res-retry-amplification", "resilience", "Retry amplification: retries at every layer multiply",
                    "If the client retries the Order service and the Order service retries Inventory, how many calls does a failing Inventory receive for one user request?",
                    "The attempts multiply: client attempts times order attempts. A service that is already failing receives the most traffic, and each client retry also "
                            + "creates another order row.",
                    "services", List.of(WhatIfOption.integer("clientAttempts", "Attempts by the client", 3, 1, 4),
                            WhatIfOption.integer("orderAttempts", "Attempts by the Order service against Inventory", 3, 1, 4)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            int clientAttempts = ctx.number("clientAttempts");
            int orderAttempts = ctx.number("orderAttempts");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] observed = new long[2];

            rec.live("seed", "Fixtures: a customer and a product with 50 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 50);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("baseline", "Healthy: one user request, one Inventory call", nodes("client", "order", "inventory"), "client", "order",
                    "With a healthy Inventory nothing is retried.", CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<OrderView> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Calls that reached Inventory", callsTo(ctx, mark, "inventory")));
                    });

            rec.live("failing", "Inventory fails; both layers retry", nodes("client", "order", "order-db", "inventory"), "client", "order",
                    "The client retries the Order service when it reports a dependency failure, and the Order service retries Inventory. Every client retry runs the whole "
                            + "order again, including creating another order row.", CodeRefIds.MICRO_RETRY, () -> {
                        ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.error());
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        PlaceOrder request = PlaceOrder.simple(ref[0], ref[1], 1).withInventoryAttempts(orderAttempts);
                        ResilientCaller.Result<OrderView> r = ResilientCaller.call(bus, null, OrderContract.PLACE_ORDER, request, Lab.SETUP, RetryPolicy.of(clientAttempts), null, null,
                                false, result -> result.code() != null && result.code().startsWith("INVENTORY_"));
                        observed[0] = callsTo(ctx, mark, "inventory");
                        observed[1] = r.attempts().size();
                        ctx.behaviours().reset(ServiceName.INVENTORY);
                        return List.of(ev("Final outcome for the user", Lab.outcome(r.last())), ev("Order requests made by the client", observed[1]),
                                ev("Calls that reached the failing Inventory", observed[0]), ev("Order rows created by the retries " + Lab.INSPECTOR_NOTE,
                                        Lab.orders(ctx, "REJECTED") + Lab.orders(ctx, "PLACED") - 1));
                    });

            rec.infer("amplification", "The multiplication", nodes("client", "order", "inventory"),
                    "Calculated from the attempts configured and the calls recorded.",
                    List.of(ev("User requests", 1), ev("Expected calls to Inventory", (long) clientAttempts * orderAttempts), ev("Observed calls to Inventory", observed[0]),
                            ev("Amplification factor", observed[0] + "x"), ev("Matches client attempts x order attempts", observed[0] == (long) clientAttempts * orderAttempts)));

            rec.concept("controlling-retries", "How systems stop it", nodes("client", "order", "inventory"),
                    "Retry at ONE layer, usually the one closest to the failing call. Give retries a budget (a fraction of normal traffic), add jitter, and use a circuit "
                            + "breaker so that a service that is clearly down is not hammered. Retrying a non-idempotent operation also repeats its side effects, as the extra "
                            + "order rows show.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Bulkhead implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("res-bulkhead", "resilience", "Bulkheads: one saturated service must not use up another's capacity",
                    "Two slow Payment requests occupy Payment's concurrency permits. Can the other services still work?",
                    "With separate permits per service, only Payment is saturated. With one shared pool, the slow Payment requests starve every service.",
                    "services", List.of(WhatIfOption.choice("permits", "Concurrency permits", "per-service", List.of("per-service", "shared"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean shared = "shared".equals(ctx.choice("permits"));
            ctx.useBulkhead(shared ? Bulkheads.Policy.shared(2) : Bulkheads.Policy.perService(2));
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            List<Bulkheads.Permit> held = new ArrayList<>();
            boolean[] others = new boolean[1];
            try {
                rec.live("seed", "Fixtures: a customer and a product with 10 units", nodes("client", "user", "product", "inventory", "payment"), "client", "user",
                        "The bulkhead is real: " + (shared ? "one pool of 2 permits shared by every service" : "2 permits for each service") + ", implemented with Semaphores.",
                        CodeRefIds.MICRO_BULKHEAD, () -> {
                            bus.startRequest();
                            ref[0] = Lab.seedUser(bus, "100.00");
                            ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 10);
                            return List.of(ev("Permit policy", shared ? "one shared pool (2 permits)" : "separate permits per service (2 each)"));
                        });

                rec.live("saturate", "Two slow Payment requests are still in progress", nodes("client", "payment"), "client", "payment",
                        "Each in-flight request holds one permit of the real Semaphore until it finishes. The lab keeps two open to model a slow Payment service.",
                        CodeRefIds.MICRO_BULKHEAD, () -> {
                            held.add(bus.holdPermit(ServiceName.PAYMENT));
                            held.add(bus.holdPermit(ServiceName.PAYMENT));
                            return List.of(ev("Permits available to Payment", bus.bulkheads().available(ServiceName.PAYMENT)),
                                    ev("Permits available to Inventory", bus.bulkheads().available(ServiceName.INVENTORY)),
                                    ev("Permits available to Product", bus.bulkheads().available(ServiceName.PRODUCT)));
                        });

                rec.live("payment-rejected", "A third Payment call is refused", nodes("client", "payment"), "client", "payment",
                        "Payment has no permit left, so the call is rejected locally and never reaches the service.", CodeRefIds.MICRO_BULKHEAD, () -> {
                            bus.startRequest();
                            CallResult<PaymentView> r = bus.call(null, PaymentContract.CHARGE, new Charge(1, new BigDecimal("9.00"), "k1"), Lab.SETUP);
                            return List.of(ev("Caller sees", Lab.outcome(r)), ev("Handler ran (known only to the lab)", r.handlerInvoked()));
                        });

                rec.live("others", "Can the other services still work?", nodes("client", "user", "product", "inventory"), "client", "inventory",
                        shared ? "Everything shares the two held permits, so every service is refused: the slow Payment requests have starved the whole system."
                                : "Each service has its own permits, so Inventory, Product and User are unaffected by Payment's saturation.", CodeRefIds.MICRO_BULKHEAD, () -> {
                            bus.startRequest();
                            CallResult<Reservation> inv = bus.call(null, InventoryContract.RESERVE, new Reserve(1, ref[1], 1), Lab.SETUP);
                            CallResult<ProductView> prod = bus.call(null, ProductContract.GET_PRODUCT, new GetProduct(ref[1]), Lab.SETUP);
                            CallResult<UserContract.UserView> usr = bus.call(null, UserContract.GET_USER, new GetUser(ref[0]), Lab.SETUP);
                            others[0] = inv.isSuccess() && prod.isSuccess() && usr.isSuccess();
                            return List.of(ev("Inventory call", Lab.outcome(inv)), ev("Product call", Lab.outcome(prod)), ev("User call", Lab.outcome(usr)));
                        });

                rec.live("released", "The slow requests finish and release their permits", nodes("client", "payment"), "client", "payment",
                        "Permits come back when the requests end, so Payment accepts calls again.", CodeRefIds.MICRO_BULKHEAD, () -> {
                            held.forEach(Bulkheads.Permit::close);
                            held.clear();
                            bus.startRequest();
                            CallResult<PaymentView> r = bus.call(null, PaymentContract.CHARGE, new Charge(1, new BigDecimal("9.00"), "k1"), Lab.SETUP);
                            return List.of(ev("Permits available to Payment", bus.bulkheads().available(ServiceName.PAYMENT)), ev("Caller sees", Lab.outcome(r)));
                        });
            } finally {
                held.forEach(Bulkheads.Permit::close);
            }

            rec.infer("isolation", "Was the failure contained?", nodes("payment", "inventory", "product", "user"),
                    "Calculated from the calls recorded in the steps above.",
                    List.of(ev("Other services stayed available", others[0]), ev("Policy", shared ? "shared pool: not isolated" : "per-service permits: isolated")));

            rec.concept("sizing", "What a bulkhead does not do", nodes("payment"),
                    "A bulkhead limits the damage a slow dependency can do to the rest of the system; it does not make that dependency faster. Real services size the "
                            + "limits from measured concurrency and usually combine them with timeouts and a queue limit.");
        }
    }
}
