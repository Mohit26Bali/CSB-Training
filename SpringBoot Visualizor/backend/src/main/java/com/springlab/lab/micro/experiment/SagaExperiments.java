package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.InventoryContract;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.OrderView;
import com.springlab.lab.micro.contract.OrderContract.Resume;
import com.springlab.lab.micro.contract.OrderContract.SagaFault;
import com.springlab.lab.micro.contract.OrderContract.SagaRequest;
import com.springlab.lab.micro.contract.OrderContract.SagaStepView;
import com.springlab.lab.micro.contract.OrderContract.SagaView;
import com.springlab.lab.micro.contract.PaymentContract;
import com.springlab.lab.micro.contract.PaymentContract.Policy;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.EventChannel;
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

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * Topic 6: distributed transactions and the saga pattern. There is no transaction across services: every step is a service LOCAL
 * transaction, and a failed workflow is undone by COMPENSATING business operations (a release, a refund). Compensation is not a database
 * rollback; the effects it reverses were committed and visible, and they stay visible as history.
 */
public final class SagaExperiments {

    private static final CallOptions DELIVERY = CallOptions.timeout(200);

    private SagaExperiments() {
    }

    private static void seedShop(RunContext ctx, ServiceBus bus, long[] ref) {
        bus.startRequest();
        ref[0] = Lab.seedUser(bus, "100.00");
        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
    }

    private static List<Evidence> sagaLog(SagaView view) {
        List<Evidence> out = new ArrayList<>();
        for (SagaStepView s : view.steps()) {
            out.add(ev("Saga log " + s.seq() + " " + s.step(), s.state() + (s.detail() == null ? "" : " (" + s.detail() + ")")));
        }
        return out;
    }

    /** The state each service keeps about this one order, read by the lab inspector. Each service owns its own part. */
    private static List<Evidence> serviceStates(RunContext ctx, long productRef, long orderRef) {
        return List.of(ev("Order service: order status " + Lab.INSPECTOR_NOTE, Lab.orderStatus(ctx, orderRef)),
                ev("Inventory service: stock " + Lab.INSPECTOR_NOTE, Lab.stock(ctx, productRef)),
                ev("Inventory service: reservations RESERVED / RELEASED " + Lab.INSPECTOR_NOTE,
                        Lab.reservationsWith(ctx, "RESERVED") + " / " + Lab.reservationsWith(ctx, "RELEASED")),
                ev("Payment service: payments CHARGED / REFUNDED " + Lab.INSPECTOR_NOTE, Lab.payments(ctx, "CHARGED") + " / " + Lab.payments(ctx, "REFUNDED")));
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Orchestrated implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("saga-orchestrated", "saga", "Saga by orchestration: one coordinator, compensations in reverse",
                    "How does the Order service place an order across Inventory and Payment, and what does it do when a step fails?",
                    "The orchestrator runs reserve, charge and confirm as separate local transactions and, when one fails, runs compensating operations for the steps that "
                            + "completed, in reverse order. A compensation can itself fail, and then the saga needs attention.",
                    "services", List.of(WhatIfOption.choice("scenario", "What happens", "payment-fails",
                            List.of("success", "inventory-fails", "payment-fails", "confirm-fails", "compensation-fails"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            String scenario = ctx.choice("scenario");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] order = new long[1];
            long[] compensated = new long[1];

            rec.live("seed", "Fixtures: a customer and a product with 5 units", nodes("client", "user", "product", "inventory", "payment"), "client", "user",
                    "Created through the services. The scenario's fault is prepared here: " + scenario + ".", null, () -> {
                        seedShop(ctx, bus, ref);
                        if (scenario.equals("payment-fails") || scenario.equals("compensation-fails")) {
                            Lab.value(bus.call(null, PaymentContract.SET_POLICY, new Policy(true), Lab.SETUP));
                        }
                        if (scenario.equals("compensation-fails")) {
                            ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.downAfter(1)); // reserve works, the later release finds Inventory down
                        }
                        return List.of(ev("Scenario", scenario));
                    });

            rec.live("saga", "The Order service orchestrates the saga", nodes("client", "order", "order-db", "inventory", "inventory-db", "payment", "payment-db"), "client", "order",
                    "Each step is a call to another service, which commits its own local transaction. The Order service writes every step and its state to its own "
                            + "saga log, and compensates in reverse order when a step fails.", CodeRefIds.MICRO_SAGA_ORCHESTRATOR, () -> {
                        int quantity = scenario.equals("inventory-fails") ? 99 : 2;
                        SagaFault fault = scenario.equals("confirm-fails") ? SagaFault.CONFIRM_FAILS : SagaFault.NONE;
                        bus.startRequest();
                        CallResult<SagaView> r = bus.call(null, OrderContract.PLACE_ORDER_SAGA, new SagaRequest(ref[0], ref[1], quantity, fault), Lab.SETUP);
                        List<Evidence> out = new ArrayList<>();
                        out.add(ev("Caller sees", Lab.outcome(r)));
                        if (r.isSuccess()) {
                            order[0] = r.value().orderRef();
                            out.add(ev("Saga ended", r.value().status()));
                            out.addAll(sagaLog(r.value()));
                            compensated[0] = r.value().steps().stream().filter(s -> s.state().equals("COMPENSATED")).count();
                        }
                        return out;
                    });

            rec.observe("states", "What each service holds after the saga", nodes("order", "inventory", "payment"),
                    "Read by the lab inspector from each service's own tables. There is no single place that holds the truth except the Order saga log; each service "
                            + "only knows its part.", serviceStates(ctx, ref[1], order[0]));

            if (scenario.equals("compensation-fails")) {
                rec.live("resume", "Inventory is back: the compensation is retried", nodes("client", "order", "order-db", "inventory", "inventory-db"), "client", "order",
                        "The saga ended COMPENSATION_FAILED and stays that way until something acts. Here an operator (the lab) resumes it once Inventory has recovered.",
                        CodeRefIds.MICRO_SAGA_ORCHESTRATOR, () -> {
                            ctx.behaviours().reset(ServiceName.INVENTORY);
                            bus.startRequest();
                            CallResult<SagaView> r = bus.call(null, OrderContract.RESUME_COMPENSATION, new Resume(order[0]), Lab.SETUP);
                            List<Evidence> out = new ArrayList<>();
                            out.add(ev("Caller sees", Lab.outcome(r)));
                            if (r.isSuccess()) {
                                out.add(ev("Saga ended", r.value().status()));
                                out.addAll(sagaLog(r.value()));
                                compensated[0] = r.value().steps().stream().filter(s -> s.state().equals("COMPENSATED")).count();
                            }
                            out.add(Lab.stockEv(ctx, ref[1]));
                            return out;
                        });
            }

            rec.infer("not-a-rollback", "Compensation is not a rollback", nodes("order", "inventory", "payment"),
                    "Calculated from the captured database calls and the saga log. The effects the saga reverses were committed by their services and could be seen by "
                            + "other readers; the compensation is a new operation that reverses them.",
                    List.of(ev("Database rollbacks during the whole saga", ctx.recorder().countDatabaseCalls("rollback")),
                            ev("Compensating business operations that completed", compensated[0]),
                            ev("The reversed reservation still exists as history " + Lab.INSPECTOR_NOTE, Lab.reservationsWith(ctx, "RELEASED") > 0 || compensated[0] == 0),
                            ev("Local transactions committed (all services)", ctx.recorder().countDatabaseCalls("commit"))));

            rec.concept("orchestration-trade-offs", "What orchestration gives and costs", nodes("order"),
                    "One place knows the whole workflow, so it is easy to follow, to monitor and to change. The price is that the orchestrator knows every participant "
                            + "(coupling to them), is itself critical, and must persist its state so it can resume after a crash. Choreography removes the coordinator and "
                            + "moves the problem elsewhere.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class Choreographed implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("saga-choreographed", "saga", "Saga by choreography: services react to each other's events",
                    "How is the same order placed when nobody coordinates and each service only reacts to events?",
                    "Each service does its own step when an event arrives and publishes the next event. Compensation is also an event reaction. No one holds the whole state, "
                            + "and a compensation that cannot run yet simply waits as a pending event.",
                    "services", List.of(WhatIfOption.choice("scenario", "What happens", "payment-fails", List.of("success", "payment-fails", "compensation-fails"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            String scenario = ctx.choice("scenario");
            boolean paymentFails = !scenario.equals("success");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] order = new long[1];

            rec.concept("what-this-shows", "No coordinator", nodes("order", "broker", "inventory", "payment"),
                    "The Order service creates the order and publishes OrderCreated. After that nothing calls anything: Inventory reacts to OrderCreated, Payment to "
                            + "StockReserved, Order to PaymentCompleted. The channel is SIMULATED (not Kafka or RabbitMQ), and these events are published after each local "
                            + "commit, not through an outbox.");

            rec.live("seed", "Fixtures, and every service subscribes to the events it reacts to", nodes("client", "user", "product", "inventory", "payment", "order", "broker"),
                    "client", "user", "The subscriptions are the whole 'workflow definition' of a choreography.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                        seedShop(ctx, bus, ref);
                        EventChannel channel = ctx.channel();
                        channel.subscribe("saga.OrderCreated", "inventory", InventoryContract.ON_ORDER_CREATED);
                        channel.subscribe("saga.PaymentFailed", "inventory", InventoryContract.ON_PAYMENT_FAILED);
                        channel.subscribe("saga.StockReserved", "payment", PaymentContract.ON_STOCK_RESERVED);
                        channel.subscribe("saga.PaymentCompleted", "order", OrderContract.ON_PAYMENT_COMPLETED);
                        channel.subscribe("saga.StockRejected", "order", OrderContract.ON_STOCK_REJECTED);
                        channel.subscribe("saga.StockReleased", "order", OrderContract.ON_STOCK_RELEASED);
                        if (paymentFails) {
                            Lab.value(bus.call(null, PaymentContract.SET_POLICY, new Policy(true), Lab.SETUP));
                        }
                        if (scenario.equals("compensation-fails")) {
                            ctx.behaviours().set(ServiceName.INVENTORY, Behaviour.downAfter(1)); // the reservation works, the later release finds Inventory down
                        }
                        return List.of(ev("Event mechanism", CommunicationExperiments.MECHANISM), ev("Scenario", scenario));
                    });

            rec.live("start", "The Order service creates the order and publishes OrderCreated", nodes("client", "order", "order-db", "broker"), "order", "broker",
                    "The order is PENDING. The only thing that has happened is one committed row and one event on the channel.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                        bus.startRequest();
                        CallResult<OrderView> r = bus.call(null, OrderContract.START_CHOREOGRAPHY, new SagaRequest(ref[0], ref[1], 2, SagaFault.NONE), Lab.SETUP);
                        order[0] = r.value().orderRef();
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Order status", r.value().status()), Lab.stockEv(ctx, ref[1]),
                                ev("Events pending for Inventory", ctx.channel().pending("inventory")));
                    });

            rec.live("inventory-reserves", "Inventory reacts to OrderCreated", nodes("broker", "inventory", "inventory-db"), "broker", "inventory",
                    "Inventory commits its local transaction and publishes StockReserved. Nobody asked it to: it reacted to an event.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                        bus.startRequest();
                        List<EventChannel.Attempt> a = ctx.channel().deliverPending("inventory", DELIVERY);
                        return List.of(ev("Delivery", a.get(0).outcome()), Lab.stockEv(ctx, ref[1]), ev("Events pending for Payment", ctx.channel().pending("payment")));
                    });

            rec.observe("in-between", "Between two local transactions", nodes("order", "inventory", "payment"),
                    "The stock is already reduced, the order is still PENDING and nothing has been charged. Other readers can see this intermediate state: there is no "
                            + "isolation across services.", serviceStates(ctx, ref[1], order[0]));

            rec.live("payment-reacts", "Payment reacts to StockReserved", nodes("broker", "payment", "payment-db"), "broker", "payment",
                    paymentFails ? "The customer's payment is declined, so Payment publishes PaymentFailed instead of PaymentCompleted."
                            : "The payment is taken and Payment publishes PaymentCompleted.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                        bus.startRequest();
                        List<EventChannel.Attempt> a = ctx.channel().deliverPending("payment", DELIVERY);
                        return List.of(ev("Delivery", a.get(0).outcome()), ev("Payments CHARGED " + Lab.INSPECTOR_NOTE, Lab.payments(ctx, "CHARGED")),
                                ev("Events pending for Order", ctx.channel().pending("order")), ev("Events pending for Inventory", ctx.channel().pending("inventory")));
                    });

            if (!paymentFails) {
                rec.live("order-confirms", "Order reacts to PaymentCompleted", nodes("broker", "order", "order-db"), "broker", "order",
                        "The order becomes PLACED. That is the end of the saga: every service did its own step.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                            bus.startRequest();
                            List<EventChannel.Attempt> a = ctx.channel().deliverPending("order", DELIVERY);
                            return List.of(ev("Delivery", a.get(0).outcome()), ev("Order status " + Lab.INSPECTOR_NOTE, Lab.orderStatus(ctx, order[0])));
                        });
            } else {
                rec.live("inventory-compensates", "Inventory reacts to PaymentFailed by releasing the stock", nodes("broker", "inventory", "inventory-db"), "broker", "inventory",
                        "The compensation is also an event reaction: a NEW business operation that gives the stock back. It is not a database rollback.",
                        CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                            bus.startRequest();
                            List<EventChannel.Attempt> a = ctx.channel().deliverPending("inventory", DELIVERY);
                            List<Evidence> out = new ArrayList<>();
                            out.add(ev("Delivery", a.get(0).outcome() + (a.get(0).code() == null ? "" : " (" + a.get(0).code() + ")")));
                            out.add(Lab.stockEv(ctx, ref[1]));
                            out.add(ev("Reservations RELEASED " + Lab.INSPECTOR_NOTE, Lab.reservationsWith(ctx, "RELEASED")));
                            out.add(ev("Events pending for Inventory", ctx.channel().pending("inventory")));
                            return out;
                        });
                rec.live("order-reacts", "Order reacts to what has been published", nodes("broker", "order", "order-db"), "broker", "order",
                        scenario.equals("compensation-fails") ? "Nothing arrives for Order: the release has not happened, so StockReleased was never published. The order stays PENDING."
                                : "StockReleased arrives and the order becomes CANCELLED.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                            bus.startRequest();
                            List<EventChannel.Attempt> a = ctx.channel().deliverPending("order", DELIVERY);
                            return List.of(ev("Deliveries", a.size()), ev("Order status " + Lab.INSPECTOR_NOTE, Lab.orderStatus(ctx, order[0])));
                        });
                if (scenario.equals("compensation-fails")) {
                    rec.live("recovery", "Inventory recovers: the waiting event is delivered", nodes("broker", "inventory", "inventory-db", "order"), "broker", "inventory",
                            "The compensating event never left the channel, so when Inventory is back it is delivered and the saga completes by itself. Choreography "
                                    + "gets this retry from at-least-once delivery.", CodeRefIds.MICRO_SAGA_CHOREOGRAPHY, () -> {
                                ctx.behaviours().reset(ServiceName.INVENTORY);
                                bus.startRequest();
                                List<EventChannel.Attempt> a = ctx.channel().deliverAll(DELIVERY, 10);
                                return List.of(ev("Deliveries", a.size()), Lab.stockEv(ctx, ref[1]), ev("Order status " + Lab.INSPECTOR_NOTE, Lab.orderStatus(ctx, order[0])));
                            });
                }
            }

            List<Evidence> end = new ArrayList<>(serviceStates(ctx, ref[1], order[0]));
            end.add(ev("Events published in total", ctx.channel().published()));
            end.add(ev("Database rollbacks during the whole saga", ctx.recorder().countDatabaseCalls("rollback")));
            end.add(ev("A component that held the whole saga state", "none: it is spread over the order, reservation and payment rows"));
            rec.infer("no-coordinator", "Who knew the state of the saga?", nodes("order", "inventory", "payment"),
                    "Calculated from the final state of each service. Compare with orchestration, where the saga log is in one place.", end);
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class CompensationVsRollback implements MicroExperiment {

        private final MonolithOrderWorkflow monolith;

        CompensationVsRollback(MonolithOrderWorkflow monolith) {
            this.monolith = monolith;
        }

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("saga-compensation-vs-rollback", "saga", "A rollback leaves nothing; a compensation leaves history",
                    "What is the difference between undoing work with a database rollback and undoing it with a compensating operation?",
                    "A rollback makes the work as if it never happened, and nobody ever saw it. A compensation reverses effects that were committed and visible, and the "
                            + "effects stay in the data as history.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[3];
            long[] rollbacks = new long[2];

            rec.live("seed", "Fixtures: a customer and two products with 5 units each", nodes("client", "user", "product", "inventory", "payment"), "client", "user",
                    "One product is used for the rollback, the other for the compensation. Payment is set to decline the customer.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "ROLLBACK", "4.50", 5);
                        ref[2] = Lab.seedProduct(bus, "COMPENSATE", "4.50", 5);
                        Lab.value(bus.call(null, PaymentContract.SET_POLICY, new Policy(true), Lab.SETUP));
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("rollback", "One local transaction fails: MySQL rolls it back", nodes("client", "monolith", "monolith-db"), "client", "monolith",
                    "Order, stock and reservation are written in ONE transaction and the workflow fails after the reservation. The database rolls everything back.",
                    CodeRefIds.MICRO_MONOLITH_TX, () -> {
                        long before = ctx.recorder().countDatabaseCalls("rollback");
                        try {
                            monolith.place(ref[0], ref[1], 2, true);
                        } catch (RuntimeException expected) {
                            // the injected failure
                        }
                        rollbacks[0] = ctx.recorder().countDatabaseCalls("rollback") - before;
                        return List.of(ev("Database rollbacks observed", rollbacks[0]), Lab.stockEv(ctx, ref[1]),
                                ev("Orders for the product " + Lab.INSPECTOR_NOTE, Lab.ordersOf(ctx, ref[1], "PENDING") + Lab.ordersOf(ctx, ref[1], "PLACED")),
                                ev("Reservations " + Lab.INSPECTOR_NOTE, Lab.reservations(ctx)));
                    });

            rec.live("compensation", "Separate services: the saga compensates", nodes("client", "order", "inventory", "payment"), "client", "order",
                    "Inventory commits the reservation, Payment declines, and the Order service asks Inventory to release the stock. Nothing is rolled back: each step "
                            + "was committed.", CodeRefIds.MICRO_SAGA_ORCHESTRATOR, () -> {
                        long before = ctx.recorder().countDatabaseCalls("rollback");
                        bus.startRequest();
                        CallResult<SagaView> r = bus.call(null, OrderContract.PLACE_ORDER_SAGA, new SagaRequest(ref[0], ref[2], 2, SagaFault.NONE), Lab.SETUP);
                        rollbacks[1] = ctx.recorder().countDatabaseCalls("rollback") - before;
                        List<Evidence> out = new ArrayList<>();
                        out.add(ev("Caller sees", Lab.outcome(r)));
                        out.add(ev("Saga ended", r.value().status()));
                        out.addAll(sagaLog(r.value()));
                        out.add(ev("Database rollbacks observed", rollbacks[1]));
                        out.add(Lab.stockEv(ctx, ref[2]));
                        out.add(ev("Reservations RELEASED " + Lab.INSPECTOR_NOTE, Lab.reservationsWith(ctx, "RELEASED")));
                        return out;
                    });

            rec.infer("difference", "What each approach left in the data", nodes("monolith", "inventory"),
                    "Calculated from the two steps shown above.",
                    List.of(ev("After the rollback: reservation rows left for that product", Lab.reservationsOf(ctx, ref[1])),
                            ev("After the compensation: reservation rows left for that product (history)", Lab.reservationsOf(ctx, ref[2])),
                            ev("After the compensation: those rows are RELEASED", Lab.reservationsWith(ctx, "RELEASED") == Lab.reservationsOf(ctx, ref[2])),
                            ev("A rollback happened in the single transaction", rollbacks[0] > 0), ev("A rollback happened in the saga", rollbacks[1] > 0)));

            rec.concept("two-phase-commit", "Why not one distributed transaction?", nodes("order", "inventory", "payment"),
                    "Two-phase commit would ask every participant to PREPARE and then, if all agree, to COMMIT, so the three services would behave like one database "
                            + "transaction. It is not run here. It needs every participant to support it, holds locks across the network while waiting, and blocks everyone if "
                            + "the coordinator fails. Sagas give up isolation (others can see the intermediate state) in exchange for services that stay independent and available.");
        }
    }
}
