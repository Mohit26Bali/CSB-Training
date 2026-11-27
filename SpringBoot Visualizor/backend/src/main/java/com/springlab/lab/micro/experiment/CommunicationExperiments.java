package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.NotificationContract;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.NotifyMode;
import com.springlab.lab.micro.contract.OrderContract.PlaceOrder;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallRecord;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.EventChannel;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.Outcome;
import com.springlab.lab.micro.core.ServiceBehaviours.Behaviour;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.core.ServiceName;
import com.springlab.lab.micro.core.Span;
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
 * Topic 2: service communication. Synchronous calls (the caller waits) and events on the SIMULATED in-process channel (the sender
 * does not wait). The channel is not Kafka or RabbitMQ, and publishing an event after a commit is not a transactional outbox.
 */
public final class CommunicationExperiments {

    static final String MECHANISM = "SIMULATED in-process event channel (not Kafka or RabbitMQ)";
    private static final CallOptions CONSUMER_TIMEOUT = CallOptions.timeout(200);

    private CommunicationExperiments() {
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class SyncOrderCalls implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("comm-sync-order-calls", "communication", "Synchronous calls: the Order service asks three other services",
                    "What does placing an order cost in calls and time when every dependency is called synchronously?",
                    "The Order service waits for User, Product and Inventory one after another, so the time of the order is its own time plus all of theirs.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] root = new long[1];
            int[] mark = new int[1];

            rec.live("seed", "Fixtures: a customer and a product with 5 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("place-order", "The client calls the Order service, which calls three services in turn", nodes("client", "order", "order-db", "user", "product", "inventory", "inventory-db"),
                    "client", "order",
                    "Each arrow is a synchronous request/response: the Order service cannot continue until the answer arrives. The calls are made one "
                            + "after the other, each as a child of the order request.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        mark[0] = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 2), Lab.SETUP);
                        root[0] = r.simulatedMillis();
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.ordersEv(ctx, "PLACED"), Lab.stockEv(ctx, ref[1]));
                    });

            List<CallRecord> calls = ctx.traces().callsSince(mark[0]);
            long children = 0;
            List<Evidence> evidence = new ArrayList<>();
            for (CallRecord c : calls) {
                if (c.parentSpanId() != null) {
                    children += c.simulatedMillis();
                }
                evidence.add(ev("Call " + c.seq() + (c.parentSpanId() == null ? " (the request)" : " (made by the Order service)"),
                        c.to() + "." + c.operation() + ": " + c.outcome() + ", " + c.simulatedMillis() + " ms simulated"));
            }
            evidence.add(ev("Order service own time (simulated)", ServiceBus.baseLatency(ServiceName.ORDER) + " ms"));
            evidence.add(ev("Sum of the dependency calls (simulated)", children + " ms"));
            evidence.add(ev("Total equals own time plus the sum of the calls", root[0] == ServiceBus.baseLatency(ServiceName.ORDER) + children));
            rec.infer("time-adds-up", "Where the time went", nodes("order", "user", "product", "inventory"),
                    "Calculated from the recorded spans. Because the calls are sequential, their times add up; a slow dependency slows the whole "
                            + "order directly. Times are SIMULATED (a logical clock), not measurements.", evidence);

            rec.concept("sync-trade-offs", "What synchronous communication gives and costs", nodes("order", "inventory"),
                    "Request/response is simple: the answer is available when the call returns and errors come back immediately. The price is "
                            + "temporal coupling (both services must be up at the same moment) and latency that accumulates along the chain.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class SyncFailure implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("comm-sync-failure", "communication", "A synchronous call fails: what is left behind?",
                    "One service the Order service depends on fails in one of three ways. What does the caller see, and what state remains?",
                    "A refused or unreachable call changes nothing. A slow response is different: the work may have been done, so the order is left "
                            + "PENDING with stock reserved.",
                    "services", List.of(
                            WhatIfOption.choice("failingService", "The service that fails", "inventory", List.of("user", "product", "inventory")),
                            WhatIfOption.choice("fault", "How it fails", "slow", List.of("down", "slow", "error"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceName failing = switch (ctx.choice("failingService")) {
                case "user" -> ServiceName.USER;
                case "product" -> ServiceName.PRODUCT;
                default -> ServiceName.INVENTORY;
            };
            String fault = ctx.choice("fault");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            Outcome[] seen = new Outcome[1];

            rec.live("seed", "Fixtures: a customer and a product with 5 units", nodes("client", "user", "product", "inventory"), "client", "user",
                    "Created through the services that own them.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        return List.of(ev("Fixtures", "created"));
                    });

            rec.live("healthy", "Baseline: everything is up", nodes("client", "order", "user", "product", "inventory"), "client", "order",
                    "With all dependencies healthy the order is placed.", CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.ordersEv(ctx, "PLACED"), Lab.stockEv(ctx, ref[1]));
                    });

            rec.live("faulted", "The " + failing.id() + " service " + faultText(fault), nodes("client", "order", "order-db", failing.id()), "client", "order",
                    "The Order service waits at most 100 simulated ms for each dependency. What it can conclude depends on HOW the call failed, not "
                            + "just that it failed.",
                    CodeRefIds.MICRO_BUS_CALL, () -> {
                        ctx.behaviours().set(failing, switch (fault) {
                            case "down" -> Behaviour.down();
                            case "error" -> Behaviour.error();
                            default -> Behaviour.delayed(300);
                        });
                        int mark = ctx.traces().callCount();
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withTimeout(100), Lab.SETUP);
                        ctx.behaviours().reset(failing);
                        for (CallRecord c : ctx.traces().callsSince(mark)) {
                            if (c.to().equals(failing.id())) {
                                seen[0] = c.outcome();
                            }
                        }
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Orders PLACED " + Lab.INSPECTOR_NOTE, Lab.orders(ctx, "PLACED")),
                                ev("Orders PENDING " + Lab.INSPECTOR_NOTE, Lab.orders(ctx, "PENDING")),
                                ev("Orders REJECTED " + Lab.INSPECTOR_NOTE, Lab.orders(ctx, "REJECTED")), Lab.stockEv(ctx, ref[1]),
                                ev("Reservations " + Lab.INSPECTOR_NOTE, Lab.reservations(ctx)));
                    });

            rec.infer("conclusion", "What the Order service could conclude", nodes("order", failing.id()),
                    "Calculated from the outcome the Order service saw from the failing dependency. The wording is the same everywhere in the lab.",
                    List.of(ev("Outcome of the call to " + failing.id(), String.valueOf(seen[0])),
                            ev("What can be concluded", seen[0] == null ? "the failing service was not reached" : Lab.meaning(seen[0]))));

            rec.concept("retry-warning", "Would a retry help?", nodes("client", "order"),
                    "Retrying after UNAVAILABLE is safe because nothing was processed. Retrying after a TIMEOUT can duplicate work unless the operation is "
                            + "idempotent. Retries, idempotency keys and circuit breakers are the Resilience topic.");
        }

        private static String faultText(String fault) {
            return switch (fault) {
                case "down" -> "is down";
                case "error" -> "fails with an unexpected error";
                default -> "answers too slowly";
            };
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class AsyncEvent implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("comm-async-event", "communication", "Asynchronous communication: publish now, consume later",
                    "What changes when the Order service announces an order with an event instead of calling Notification?",
                    "The order is accepted without waiting for the consumer. The consumer processes the event later, in a separate step.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] orderMillis = new long[1];

            rec.concept("what-the-channel-is", "What the event channel is in this lab", nodes("order", "broker", "notification"),
                    "The channel is an in-memory list inside SpringLab, created for this run. It is SIMULATED: no Kafka, no RabbitMQ, no persistence, "
                            + "no partitions and no network. It lets you see the difference between a call the sender waits for and an event it does not.");

            rec.live("seed", "Fixtures, and the Notification consumer subscribes", nodes("client", "user", "product", "inventory", "broker", "notification"), "client", "user",
                    "The consumer subscribes to the 'orders' topic of the simulated channel.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        ctx.channel().subscribe("orders", "notification", NotificationContract.ON_ORDER_PLACED);
                        return List.of(ev("Event mechanism", MECHANISM));
                    });

            rec.live("publish", "The order is placed and an event is published", nodes("client", "order", "order-db", "broker"), "order", "broker",
                    "After the order is committed, the Order service appends an OrderPlaced event to the channel and returns. It does not wait for "
                            + "Notification, and Notification has not seen the event yet. The publication is a separate step after the commit, not part of "
                            + "the order transaction.",
                    CodeRefIds.MICRO_EVENT_CHANNEL, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.ASYNC), Lab.SETUP);
                        orderMillis[0] = r.simulatedMillis();
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Event mechanism", MECHANISM),
                                ev("Events published", ctx.channel().published()), ev("Events pending for Notification", ctx.channel().pending("notification")),
                                Lab.notificationsEv(ctx));
                    });

            rec.live("consume", "The consumer processes the event later", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "Delivery is its own step, with its own request and span. The consumer records the notification in its own local transaction.",
                    CodeRefIds.MICRO_IDEMPOTENT_CONSUMER, () -> {
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        return List.of(ev("Delivery attempts", attempts.size()), ev("First attempt", attempts.get(0).outcome()),
                                ev("Events pending for Notification", ctx.channel().pending("notification")), Lab.notificationsEv(ctx));
                    });

            rec.infer("compare", "Synchronous versus asynchronous, in this run", nodes("order", "notification"),
                    "The order did not include Notification's time. The notification happened afterwards, in a different request.",
                    List.of(ev("Order took (simulated)", orderMillis[0] + " ms, without waiting for Notification"),
                            ev("Notification time (simulated)", ServiceBus.baseLatency(ServiceName.NOTIFICATION) + " ms, in a separate delivery"),
                            ev("What was given up", "the caller no longer learns immediately whether the notification succeeded")));

            rec.concept("real-brokers", "How real brokers differ", nodes("broker"),
                    "A real broker (Kafka, RabbitMQ) stores messages durably, can be down, delivers at-least-once, and may reorder or redeliver. "
                            + "None of that is run here. The next experiments show the consequences you must design for.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class AsyncDuplicate implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("comm-async-duplicate", "communication", "An event is delivered more than once",
                    "What happens when the same event reaches the consumer again, with and without a consumer that remembers message ids?",
                    "A consumer without deduplication repeats its side effect for every delivery. An idempotent consumer records the message id in the "
                            + "same local transaction as its effect, so a duplicate changes nothing.",
                    "services", List.of(
                            WhatIfOption.choice("consumer", "The consumer", "naive", List.of("idempotent", "naive")),
                            WhatIfOption.integer("extraDeliveries", "Extra deliveries of the same event", 1, 1, 3)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean idempotent = "idempotent".equals(ctx.choice("consumer"));
            int extra = ctx.number("extraDeliveries");
            Operation<EventMessage, NotificationContract.Delivery> operation =
                    idempotent ? NotificationContract.ON_ORDER_PLACED : NotificationContract.ON_ORDER_PLACED_NAIVE;
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            String[] messageId = new String[1];
            long[] result = new long[2];

            rec.live("seed", "Fixtures, and the " + (idempotent ? "idempotent" : "naive") + " consumer subscribes", nodes("client", "user", "product", "inventory", "broker", "notification"),
                    "client", "user", "The channel is SIMULATED (not Kafka or RabbitMQ).", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 5);
                        ctx.channel().subscribe("orders", "notification", operation);
                        return List.of(ev("Consumer", idempotent ? "remembers processed message ids" : "no deduplication"), ev("Event mechanism", MECHANISM));
                    });

            rec.live("publish", "Place an order and publish OrderPlaced", nodes("client", "order", "order-db", "broker"), "order", "broker",
                    "One order is placed and one OrderPlaced event is published to the simulated channel.", CodeRefIds.MICRO_EVENT_CHANNEL, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.ASYNC), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Events pending for Notification", ctx.channel().pending("notification")));
                    });

            rec.live("first-delivery", "The first delivery", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "The event is delivered and acknowledged. Exactly one notification exists.", CodeRefIds.MICRO_IDEMPOTENT_CONSUMER, () -> {
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        messageId[0] = attempts.get(0).messageId();
                        return List.of(ev("Delivered", attempts.get(0).messageId() + " -> " + attempts.get(0).outcome()), Lab.notificationsEv(ctx),
                                ev("Processed message ids recorded " + Lab.INSPECTOR_NOTE, Lab.processed(ctx)));
                    });

            rec.live("duplicate-delivery", "The channel delivers the same event " + extra + " more time(s)", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "At-least-once delivery means a consumer can see the same message again, for example after a lost acknowledgement. Here the "
                            + "lab forces the duplicate deliveries.",
                    CodeRefIds.MICRO_IDEMPOTENT_CONSUMER, () -> {
                        List<Evidence> out = new ArrayList<>();
                        for (int i = 1; i <= extra; i++) {
                            bus.startRequest();
                            EventChannel.Attempt attempt = ctx.channel().redeliver("notification", messageId[0], CONSUMER_TIMEOUT);
                            out.add(ev("Duplicate delivery " + i, attempt.outcome()));
                        }
                        result[0] = Lab.notifications(ctx);
                        result[1] = Lab.processed(ctx);
                        out.add(Lab.notificationsEv(ctx));
                        out.add(ev("Processed message ids recorded " + Lab.INSPECTOR_NOTE, result[1]));
                        return out;
                    });

            rec.infer("conclusion", "Was the side effect repeated?", nodes("notification"),
                    "Calculated from the notification count after the duplicates: one order should produce one notification.",
                    List.of(ev("Notifications for one order", result[0]), ev("Side effect repeated", result[0] > 1),
                            ev("Why", idempotent ? "the message id was already recorded, so the duplicates were ignored"
                                    : "nothing remembered the message id, so every delivery sent another notification")));

            rec.concept("exactly-once", "Why 'exactly once' is not something to rely on", nodes("broker", "notification"),
                    "Networks and brokers can lose acknowledgements, so a sender cannot know whether a delivery arrived. Systems therefore deliver at least "
                            + "once and make the consumer idempotent. Recording the message id in the SAME local transaction as the effect is what makes that safe.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class SyncVsAsync implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("comm-sync-vs-async", "communication", "Synchronous or asynchronous notification, healthy and failing",
                    "How do the two ways of telling Notification behave when it is healthy, and when it is down?",
                    "Synchronous notification adds Notification's time to every order and, when Notification is down, reports a failure for an order that "
                            + "WAS placed. Asynchronous notification leaves the order unaffected and delivers later.",
                    "services", List.of(WhatIfOption.bool("notificationDown", "Notification is down during the second pair of orders", true)));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean down = ctx.flag("notificationDown");
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] millis = new long[2];

            rec.live("seed", "Fixtures: a customer and a product with 10 units", nodes("client", "user", "product", "inventory", "broker", "notification"), "client", "user",
                    "The consumer subscribes to the SIMULATED channel.", null, () -> {
                        bus.startRequest();
                        ref[0] = Lab.seedUser(bus, "100.00");
                        ref[1] = Lab.seedProduct(bus, "SKU-1", "4.50", 10);
                        ctx.channel().subscribe("orders", "notification", NotificationContract.ON_ORDER_PLACED);
                        return List.of(ev("Event mechanism", MECHANISM));
                    });

            rec.live("sync-healthy", "Synchronous notification, Notification healthy", nodes("client", "order", "notification", "notification-db"), "order", "notification",
                    "The Order service calls Notification and waits for it.", CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.SYNC), Lab.SETUP);
                        millis[0] = r.simulatedMillis();
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Order took (simulated)", millis[0] + " ms"), Lab.notificationsEv(ctx));
                    });

            rec.live("async-healthy", "Asynchronous notification, Notification healthy", nodes("client", "order", "broker", "notification"), "order", "broker",
                    "The Order service publishes an event and returns.", CodeRefIds.MICRO_EVENT_CHANNEL, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.ASYNC), Lab.SETUP);
                        millis[1] = r.simulatedMillis();
                        bus.startRequest();
                        ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Order took (simulated)", millis[1] + " ms"), Lab.notificationsEv(ctx),
                                ev("Events pending for Notification", ctx.channel().pending("notification")));
                    });

            rec.live("sync-faulted", "Synchronous notification" + (down ? ", Notification down" : ", Notification healthy"), nodes("client", "order", "order-db", "notification"), "order",
                    "notification",
                    "The order is committed BEFORE Notification is called. If the call then fails, the caller is told the request failed, although "
                            + "the order exists.",
                    CodeRefIds.MICRO_ORDER_PLACE, () -> {
                        if (down) {
                            ctx.behaviours().set(ServiceName.NOTIFICATION, Behaviour.down());
                        }
                        long before = Lab.orders(ctx, "PLACED");
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.SYNC), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("The order was placed anyway", Lab.orders(ctx, "PLACED") == before + 1),
                                Lab.notificationsEv(ctx));
                    });

            rec.live("async-faulted", "Asynchronous notification" + (down ? ", Notification down" : ", Notification healthy"), nodes("client", "order", "broker", "notification"), "order",
                    "broker", "The order does not depend on Notification, so it succeeds; the event waits for the consumer.", CodeRefIds.MICRO_EVENT_CHANNEL, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_ORDER, PlaceOrder.simple(ref[0], ref[1], 1).withNotify(NotifyMode.ASYNC), Lab.SETUP);
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Delivery attempt", attempts.isEmpty() ? "none" : String.valueOf(attempts.get(0).outcome())),
                                ev("Events pending for Notification", ctx.channel().pending("notification")));
                    });

            rec.live("recovery", "Notification recovers and the waiting event is delivered", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "Once the consumer is back, the pending event is delivered and the notification is created.", CodeRefIds.MICRO_IDEMPOTENT_CONSUMER, () -> {
                        ctx.behaviours().reset(ServiceName.NOTIFICATION);
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        return List.of(ev("Delivery attempts", attempts.size()), ev("Events pending for Notification", ctx.channel().pending("notification")),
                                Lab.notificationsEv(ctx));
                    });

            rec.infer("compare", "What the two approaches cost", nodes("order", "notification"),
                    "Calculated from the order times above (simulated) and from what the callers saw.",
                    List.of(ev("Synchronous order took (simulated)", millis[0] + " ms"), ev("Asynchronous order took (simulated)", millis[1] + " ms"),
                            ev("Difference equals Notification's own time", millis[0] - millis[1] == ServiceBus.baseLatency(ServiceName.NOTIFICATION)),
                            ev("Trade-off", "asynchronous orders do not depend on Notification, but the notification is eventually, not immediately, consistent")));

            rec.concept("choosing", "When to choose which", nodes("order", "notification"),
                    "Use a synchronous call when the caller needs the answer to continue, such as a price or an availability check. Use an event when "
                            + "other services only need to react and the caller does not need their result. The cost of events is eventual consistency, and "
                            + "consumers that must cope with delay and duplicates.");
        }
    }
}
