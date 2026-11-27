package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.Injection;
import com.springlab.lab.micro.contract.OrderContract.NotifyMode;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.contract.NotificationContract;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.monolith.MonolithOrderWorkflow;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRecorder;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.micro.run.WhatIfOption;
import com.springlab.lab.trace.CodeRefIds;
import com.springlab.lab.trace.LifecycleTrace.Evidence;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/** Topic 1: monolith vs microservices. Four experiments, each running real code against the lab's isolated tables. */
public final class MonolithExperiments {

    private MonolithExperiments() {
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class OrderWorkflow implements MicroExperiment {

        private final MonolithOrderWorkflow monolith;

        OrderWorkflow(MonolithOrderWorkflow monolith) {
            this.monolith = monolith;
        }

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("mono-order-workflow", "monolith", "One transaction or several services: what happens when a step fails?",
                    "The same order workflow is run twice: as a monolith, in one database transaction, and as services that each commit "
                            + "their own. A failure is injected after the stock was reserved. What state is left behind?",
                    "In a monolith one transaction makes the workflow all-or-nothing. Across services each step commits on its own, so "
                            + "a failure after the Inventory reservation leaves stock taken and no completed order.",
                    "services", List.of(WhatIfOption.choice("failure", "Inject a failure after the stock is reserved", "after-reserve",
                            List.of("none", "after-reserve"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean fail = "after-reserve".equals(ctx.choice("failure"));
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[3]; // user, monolith product, distributed product
            long[] stockAfter = new long[2];
            boolean[] consistent = new boolean[2];

            rec.live("seed", "Fixtures: one customer and two products with 5 units each", nodes("client", "user", "user-db", "product", "product-db", "inventory", "inventory-db"),
                    "client", "product",
                    "The lab creates a customer and two products (one for each version of the workflow) through the User, Product and "
                            + "Inventory services, so both workflows start from the same state.",
                    null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "MONO", "4.50", 5);
                        ref[2] = Lab.seedProduct(bus, "DIST", "4.50", 5);
                        return List.of(ev("Stock of the monolith product " + Lab.INSPECTOR_NOTE, Lab.stock(ctx, ref[1])),
                                ev("Stock of the distributed product " + Lab.INSPECTOR_NOTE, Lab.stock(ctx, ref[2])));
                    });

            rec.live("monolith", "Monolith: one transaction for the whole workflow", nodes("client", "monolith", "monolith-db"), "client", "monolith",
                    "A single application reads the customer and product, creates the order, takes the stock and records the reservation inside ONE "
                            + "database transaction. If anything fails, the database rolls all of it back.",
                    CodeRefIds.MICRO_MONOLITH_TX, () -> {
                        String outcome;
                        try {
                            monolith.place(ref[0], ref[1], 2, fail);
                            outcome = "committed";
                        } catch (RuntimeException e) {
                            outcome = "rolled back (" + e.getClass().getSimpleName() + ")";
                        }
                        stockAfter[0] = Lab.stock(ctx, ref[1]);
                        long placed = Lab.ordersOf(ctx, ref[1], "PLACED");
                        long pending = Lab.ordersOf(ctx, ref[1], "PENDING");
                        consistent[0] = pending == 0 && ((placed == 1 && stockAfter[0] == 3) || (placed == 0 && stockAfter[0] == 5));
                        return List.of(ev("Transaction", outcome), ev("Stock " + Lab.INSPECTOR_NOTE, stockAfter[0]),
                                ev("Orders PLACED / PENDING " + Lab.INSPECTOR_NOTE, placed + " / " + pending));
                    });

            rec.live("distributed", "Services: each step commits on its own", nodes("client", "order", "order-db", "user", "product", "inventory", "inventory-db"),
                    "client", "order",
                    "The Order service calls User, Product and Inventory. Each service commits its own local transaction; no transaction spans "
                            + "them. The failure is injected inside the Order service after Inventory has already committed the reservation.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER,
                                PlaceOrder.simple(ref[0], ref[2], 2).withInjection(fail ? Injection.AFTER_RESERVE : Injection.NONE), Lab.SETUP);
                        stockAfter[1] = Lab.stock(ctx, ref[2]);
                        long placed = Lab.ordersOf(ctx, ref[2], "PLACED");
                        long pending = Lab.ordersOf(ctx, ref[2], "PENDING");
                        consistent[1] = pending == 0 && ((placed == 1 && stockAfter[1] == 3) || (placed == 0 && stockAfter[1] == 5));
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Stock " + Lab.INSPECTOR_NOTE, stockAfter[1]),
                                ev("Orders PLACED / PENDING " + Lab.INSPECTOR_NOTE, placed + " / " + pending));
                    });

            rec.infer("comparison", "Which version ended in a consistent state?", nodes("monolith", "order"),
                    "Calculated from the two outcomes above: a state is consistent when an order is PLACED and the stock was taken, or no order exists "
                            + "and the stock is untouched.",
                    List.of(ev("Monolith ended consistent", consistent[0]), ev("Services ended consistent", consistent[1]),
                            ev("Stock still reserved with no placed order (services)", !consistent[1])));

            rec.concept("what-fixes-it", "What gives services their atomicity back?", nodes("order", "inventory"),
                    "Nothing automatic. Services need a pattern: a saga that undoes the reservation with a compensating action when a later step fails. "
                            + "Compensation is a new business operation, not a database rollback. The Saga topic runs it.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Coupling implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("mono-coupling", "monolith", "A tightly coupled dependency decides whether you can take orders",
                    "Which dependencies of the Order service can be down while orders are still accepted, and which cannot?",
                    "A service that calls another synchronously to do its job is only as available as that dependency. A dependency that is "
                            + "informed by an event can be down without stopping the order.",
                    "services", List.of(WhatIfOption.choice("hardDependency", "The synchronous dependency that goes down", "inventory",
                            List.of("user", "product", "inventory"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceName hard = switch (ctx.choice("hardDependency")) {
                case "user" -> ServiceName.USER;
                case "product" -> ServiceName.PRODUCT;
                default -> ServiceName.INVENTORY;
            };
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            String[] outcomes = new String[2];

            rec.live("seed", "Fixtures: a customer and a product with 10 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Through the User, Product and Inventory services. The Notification consumer is subscribed to the OrderPlaced event on the "
                            + "SIMULATED event channel.",
                    null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 10);
                        ctx.channel().subscribe("orders", "notification", NotificationContract.ON_ORDER_PLACED);
                        return List.of(ev("Event mechanism", "SIMULATED in-process channel (not Kafka or RabbitMQ)"));
                    });

            rec.live("healthy", "Everything is up: the order is accepted", nodes("client", "order", "order-db", "user", "product", "inventory"), "client", "order",
                    "The Order service needs User, Product and Inventory to answer before it can accept an order. They are HARD dependencies.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.ordersEv(ctx, "PLACED"));
                    });

            rec.live("hard-dependency-down", "A hard dependency is down: no orders", nodes("client", "order", hard.id()), "client", "order",
                    "With the " + hard.id() + " service unavailable, the Order service cannot do its job, so it refuses the order. The failure of one "
                            + "service has become the failure of another.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        ctx.behaviours().set(hard, Behaviour.down());
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        outcomes[0] = Lab.outcome(r);
                        ctx.behaviours().reset(hard);
                        return List.of(ev("Caller sees", outcomes[0]), Lab.ordersEv(ctx, "PLACED"),
                                ev("Orders rejected " + Lab.INSPECTOR_NOTE, Lab.orders(ctx, "REJECTED")));
                    });

            rec.live("soft-dependency-down", "A dependency informed by an event is down: orders still work", nodes("client", "order", "broker", "notification"),
                    "client", "order",
                    "Notification is told about the order by an event published after the order is committed. With Notification down, the order "
                            + "is still accepted and the event waits. This is a SOFT dependency.",
                    CodeRefIds.MICRO_EVENT_CHANNEL, () -> {
                        ctx.behaviours().set(ServiceName.NOTIFICATION, Behaviour.down());
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.ASYNC), Lab.SETUP);
                        bus.startRequest();
                        var attempts = ctx.channel().deliverPending("notification", com.springlab.lab.micro.core.CallOptions.timeout(200));
                        outcomes[1] = Lab.outcome(r);
                        return List.of(ev("Caller sees", outcomes[1]), Lab.ordersEv(ctx, "PLACED"),
                                ev("Delivery attempt to the consumer", attempts.isEmpty() ? "none" : attempts.get(0).outcome() + " (" + attempts.get(0).code() + ")"),
                                ev("Events still pending for Notification", ctx.channel().pending("notification")), Lab.notificationsEv(ctx));
                    });

            double p = 0.999;
            rec.infer("availability", "What coupling does to availability", nodes("order", "user", "product", "inventory"),
                    "Illustrative arithmetic, not a measurement: if every service were available 99.9% of the time, an operation that needs the three "
                            + "hard dependencies at once is available only when all three are. The event-informed dependency is not in that product.",
                    List.of(ev("Assumption", "each service 99.9% available (illustrative)"),
                            ev("Operation needing 3 hard dependencies", String.format("%.2f%%", Math.pow(p, 3) * 100)),
                            ev("Operation needing 1 hard dependency", String.format("%.2f%%", p * 100)),
                            ev("Observed with the " + hard.id() + " service down", outcomes[0]),
                            ev("Observed with Notification down", outcomes[1])));

            rec.concept("reducing-coupling", "Ways to loosen a dependency", nodes("order", "inventory"),
                    "A dependency can be loosened by informing the other service with an event, by caching data that rarely changes, or by degrading "
                            + "gracefully with a fallback. Each has a price: stale data, eventual consistency, or a weaker answer. Which dependencies must stay "
                            + "hard is a business decision, not a technical one.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Boundaries implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("mono-boundaries", "monolith", "Independent service boundaries: who touches which data",
                    "When an order is placed across services, which tables does each service touch?",
                    "Every table is read and written by exactly one service. Other services get data only by asking that service through its contract.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Customers, products and stock created", "through their own services"));
                    });

            rec.live("place-order", "Place an order, with a synchronous confirmation", nodes("client", "order", "order-db", "user", "user-db", "product", "product-db", "inventory", "inventory-db", "notification", "notification-db"),
                    "client", "order",
                    "The Order service needs data from three other services. It does not query their tables: it asks them through their contracts, "
                            + "and each of them reads or writes only its own tables.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 2).withNotify(NotifyMode.SYNC), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)));
                    });

            Map<String, Set<String>> touched = rec.tablesTouchedByService();
            List<Evidence> evidence = new ArrayList<>();
            java.util.Set<String> seen = new java.util.HashSet<>();
            boolean shared = false;
            for (Map.Entry<String, Set<String>> e : touched.entrySet()) {
                evidence.add(ev("Tables touched by the " + e.getKey() + " service", String.join(", ", e.getValue())));
                for (String table : e.getValue()) {
                    shared |= !seen.add(table);
                }
            }
            evidence.add(ev("A table touched by two services", shared));
            rec.infer("who-touched-what", "Which service touched which tables?", nodes("order", "user", "product", "inventory", "notification"),
                    "Calculated from the SQL each service handed to the JDBC driver in this run. The Order service touched only its own tables; the "
                            + "data of the others reached it as contract responses.", evidence);

            rec.concept("contracts-not-tables", "Why this boundary matters", nodes("order", "inventory"),
                    "If Order read the stock table directly, Inventory could never change its schema, move its data or scale independently without "
                            + "breaking Order. A contract is the only promise a service makes. The Database per Service topic shows what happens when "
                            + "the boundary is crossed.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class OverSplit implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("mono-over-split", "monolith", "Why splitting every class into a service is not good architecture",
                    "What would it cost if each internal step of placing an order were its own service?",
                    "Every extra network hop adds latency and another thing that can be down. Split along business boundaries, not along classes.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] observed = new long[3]; // calls, root ms, average hop ms

            rec.live("seed", "Fixtures: a customer and a product", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("coarse-grained", "The order workflow with business-sized services", nodes("client", "order", "user", "product", "inventory"), "client", "order",
                    "Four services cooperate: Order, User, Product and Inventory. Each hop costs simulated latency, and each can fail.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 2), Lab.SETUP);
                        List<CallRecord> calls = ctx.traces().callsSince(mark);
                        long hops = 0;
                        long sum = 0;
                        for (CallRecord c : calls) {
                            if (!c.to().equals("order")) {
                                hops++;
                                sum += c.simulatedMillis();
                            }
                        }
                        observed[0] = calls.size();
                        observed[1] = r.simulatedMillis();
                        observed[2] = hops == 0 ? 0 : sum / hops;
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Service calls in the workflow", observed[0]),
                                ev("End-to-end time (simulated)", observed[1] + " ms"), ev("Average time of a hop to another service (simulated)", observed[2] + " ms"));
                    });

            int steps = 7; // validate user, load product, price, credit check, reserve, create order, build response
            double availability = Math.pow(0.999, steps) * 100;
            rec.infer("over-split", "If each of 7 internal steps were its own service", nodes("order"),
                    "Illustrative arithmetic on the numbers observed above, not a benchmark: seven steps would mean seven hops instead of three, each "
                            + "adding the average hop time, and an operation that needs seven services at once.",
                    List.of(ev("Assumed internal steps", "validate user, load product, price, credit check, reserve, create order, build response"),
                            ev("Hops with business-sized services", observed[0] - 1), ev("Hops if every step were a service", steps),
                            ev("Estimated added time (simulated)", (steps - (observed[0] - 1)) * observed[2] + " ms"),
                            ev("Assumption", "each service 99.9% available (illustrative)"),
                            ev("Operation needing 7 services at once", String.format("%.2f%%", availability))));

            rec.concept("how-to-split", "Where to draw the lines", nodes("order", "inventory"),
                    "Split where a business capability has its own data and its own rate of change, where a team can own it end to end, or where it must "
                            + "scale or fail independently. Do not split where two parts always change together or share one transaction: that is a modular "
                            + "monolith, and for many systems it is the better architecture.");
        }
    }
}
