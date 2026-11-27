package com.springlab.lab.micro.experiment;

import com.springlab.lab.micro.contract.NotificationContract;
import com.springlab.lab.micro.contract.OrderContract;
import com.springlab.lab.micro.contract.OrderContract.OutboxOrder;
import com.springlab.lab.micro.contract.OrderContract.Relay;
import com.springlab.lab.micro.contract.OrderContract.Relayed;
import com.springlab.lab.micro.core.CallOptions;
import com.springlab.lab.micro.core.CallResult;
import com.springlab.lab.micro.core.EventChannel;
import com.springlab.lab.micro.core.EventMessage;
import com.springlab.lab.micro.core.Operation;
import com.springlab.lab.micro.core.ServiceBus;
import com.springlab.lab.micro.run.ExperimentMeta;
import com.springlab.lab.micro.run.MicroExperiment;
import com.springlab.lab.micro.run.MicroRecorder;
import com.springlab.lab.micro.run.RunContext;
import com.springlab.lab.micro.run.WhatIfOption;
import com.springlab.lab.trace.CodeRefIds;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.springlab.lab.micro.run.MicroRecorder.ev;
import static com.springlab.lab.micro.run.MicroRecorder.nodes;

/**
 * The failure window of publishing an event after a database commit, and the transactional outbox that closes it. The event channel is a
 * SIMULATED in-process list; the outbox is a REAL table written in the same local transaction as the order. Delivery is at-least-once:
 * nothing here claims exactly-once.
 */
public final class OutboxExperiments {

    private static final CallOptions CONSUMER_TIMEOUT = CallOptions.timeout(200);

    private OutboxExperiments() {
    }

    private static long[] seed(RunContext ctx, ServiceBus bus) {
        bus.startRequest();
        return new long[] {Lab.seedUser(bus, "100.00"), Lab.seedProduct(bus, "SKU-1", "4.50", 5)};
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class DualWriteGap implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("outbox-dual-write-gap", "saga", "The failure window of 'commit, then publish'",
                    "What if the Order service commits the order and then fails before it publishes the event?",
                    "The database and the channel are two systems that cannot commit together. A failure between the two writes leaves an order that nobody "
                            + "is ever told about.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];

            rec.concept("the-gap", "Two writes, one moment of risk", nodes("order", "order-db", "broker"),
                    "'Save the order, then publish the event' is two separate writes to two systems. There is no transaction that covers both, so between "
                            + "them there is a window. This experiment shows what lives in that window. Note that publishing the event after the commit is NOT a "
                            + "transactional outbox.");

            rec.live("seed", "Fixtures, and the Notification consumer subscribes", nodes("client", "user", "product", "broker", "notification"), "client", "user",
                    "The channel is SIMULATED (not Kafka or RabbitMQ).", null, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        ctx.channel().subscribe("orders", "notification", NotificationContract.ON_ORDER_PLACED);
                        return List.of(ev("Event mechanism", CommunicationExperiments.MECHANISM));
                    });

            rec.live("dual-write-works", "Commit, then publish: it works when nothing fails", nodes("client", "order", "order-db", "broker", "notification"), "order", "broker",
                    "The order commits, the event is published, the consumer later sends the notification.", CodeRefIds.MICRO_OUTBOX_WRITE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_DUAL_WRITE, new OutboxOrder(ref[0], ref[1], 1, false), Lab.SETUP);
                        bus.startRequest();
                        ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.ordersEv(ctx, "PLACED"), ev("Events published", ctx.channel().published()),
                                Lab.notificationsEv(ctx));
                    });

            rec.live("dual-write-crash", "The process fails between the commit and the publish", nodes("client", "order", "order-db", "broker"), "order", "order-db",
                    "The order is committed. Then the process fails before it reaches the line that publishes. The caller sees an error, the order exists, and "
                            + "no event was ever published.", CodeRefIds.MICRO_OUTBOX_WRITE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_DUAL_WRITE, new OutboxOrder(ref[0], ref[1], 1, true), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), Lab.ordersEv(ctx, "PLACED"), ev("Events published", ctx.channel().published()),
                                ev("Events pending for Notification", ctx.channel().pending("notification")));
                    });

            rec.infer("consequence", "What was lost", nodes("order", "notification"),
                    "Calculated from the counts above. Nothing will retry: no record remains that an event was owed.",
                    List.of(ev("Orders committed", Lab.orders(ctx, "PLACED")), ev("Events ever published", ctx.channel().published()),
                            ev("Orders nobody was told about", Lab.orders(ctx, "PLACED") - ctx.channel().published()),
                            ev("Notifications that will never be sent", Lab.orders(ctx, "PLACED") - Lab.notifications(ctx))));
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class CommitRollback implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("outbox-commit-rollback", "saga", "The transactional outbox: the order and its event commit or vanish together",
                    "What if the order row and the event to publish are written in the same local transaction?",
                    "Both commit together or both roll back together, so the order and the obligation to announce it can never get out of step. Publishing happens "
                            + "afterwards, in a separate step.",
                    "services", List.of());
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            long[] counts = new long[4];

            rec.live("seed", "Fixtures, and the Notification consumer subscribes", nodes("client", "user", "product", "broker", "notification"), "client", "user",
                    "The channel is SIMULATED (not Kafka or RabbitMQ).", null, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        ctx.channel().subscribe("orders", "notification", NotificationContract.ON_ORDER_PLACED);
                        return List.of(ev("Event mechanism", CommunicationExperiments.MECHANISM));
                    });

            rec.live("committed-together", "The order and its outbox row commit together", nodes("client", "order", "order-db"), "client", "order",
                    "Two rows, one local transaction: the order and an outbox row describing the OrderPlaced event. Nothing is published yet.",
                    CodeRefIds.MICRO_OUTBOX_WRITE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_WITH_OUTBOX, new OutboxOrder(ref[0], ref[1], 1, false), Lab.SETUP);
                        counts[0] = Lab.orders(ctx, "PLACED");
                        counts[1] = Lab.outbox(ctx, "PENDING");
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Orders PLACED " + Lab.INSPECTOR_NOTE, counts[0]),
                                ev("Outbox rows PENDING " + Lab.INSPECTOR_NOTE, counts[1]), ev("Events published", ctx.channel().published()));
                    });

            rec.live("rolled-back-together", "A failure before the commit removes both", nodes("client", "order", "order-db"), "client", "order",
                    "The same operation fails after both rows were written but before the transaction commits. MySQL rolls the transaction back, and neither "
                            + "the order nor its outbox row exists. This IS a database rollback: it is all inside one local transaction.",
                    CodeRefIds.MICRO_OUTBOX_WRITE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_WITH_OUTBOX, new OutboxOrder(ref[0], ref[1], 1, true), Lab.SETUP);
                        counts[2] = Lab.orders(ctx, "PLACED");
                        counts[3] = Lab.outbox(ctx, "PENDING");
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Orders PLACED " + Lab.INSPECTOR_NOTE, counts[2]),
                                ev("Outbox rows PENDING " + Lab.INSPECTOR_NOTE, counts[3]));
                    });

            rec.live("relay", "A separate relay publishes the committed rows", nodes("order", "order-db", "broker"), "order", "broker",
                    "The relay reads PENDING outbox rows, publishes each event, then marks the row PUBLISHED. It runs after the commit, as its own step.",
                    CodeRefIds.MICRO_OUTBOX_RELAY, () -> {
                        bus.startRequest();
                        CallResult<Relayed> r = bus.call(null, OrderContract.RELAY_OUTBOX, new Relay(false), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Events published", ctx.channel().published()),
                                ev("Outbox rows PENDING " + Lab.INSPECTOR_NOTE, Lab.outbox(ctx, "PENDING")),
                                ev("Outbox rows PUBLISHED " + Lab.INSPECTOR_NOTE, Lab.outbox(ctx, "PUBLISHED")), Lab.notificationsEv(ctx));
                    });

            rec.live("consume", "The consumer processes the event", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "As before, delivery is a separate step with its own request.", CodeRefIds.MICRO_IDEMPOTENT_CONSUMER, () -> {
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        return List.of(ev("Delivery attempts", attempts.size()), Lab.notificationsEv(ctx));
                    });

            rec.infer("never-out-of-step", "Were the order and its event ever out of step?", nodes("order", "broker"),
                    "Calculated from the counts after the commit and after the rollback.",
                    List.of(ev("After the commit: orders = outbox rows", counts[0] == counts[1]), ev("After the rollback: orders = outbox rows", counts[2] == counts[3]),
                            ev("Delivery guarantee", "at-least-once; exactly-once is not claimed")));

            rec.concept("outbox-costs", "What the outbox costs", nodes("order", "order-db", "broker"),
                    "The outbox removes the dual write, but adds a table, a relay process to run and monitor, and the possibility of duplicates and delay between the commit "
                            + "and the publication. In production the relay is often a polling job or a change-data-capture tool; here it is one explicit call so you can see each step.");
        }
    }

    // ------------------------------------------------------------------------------------------------------------------------

    @Component
    public static class DuplicateDelivery implements MicroExperiment {

        @Override
        public ExperimentMeta meta() {
            return new ExperimentMeta("outbox-duplicate-delivery", "saga", "The outbox is at-least-once: duplicates and the idempotent consumer",
                    "What if the relay publishes a row and crashes before marking it done?",
                    "The row is published again with the same message id. A consumer that remembers message ids does the work once; one that does not does it twice.",
                    "services", List.of(WhatIfOption.choice("consumer", "The consumer", "idempotent", List.of("idempotent", "naive"))));
        }

        @Override
        public void run(RunContext ctx) throws Exception {
            boolean idempotent = "idempotent".equals(ctx.choice("consumer"));
            Operation<EventMessage, NotificationContract.Delivery> operation =
                    idempotent ? NotificationContract.ON_ORDER_PLACED : NotificationContract.ON_ORDER_PLACED_NAIVE;
            ServiceBus bus = ctx.bus();
            MicroRecorder rec = ctx.recorder();
            long[] ref = new long[2];
            Set<String> ids = new HashSet<>();
            int[] deliveries = new int[1];

            rec.live("seed", "Fixtures, and the " + (idempotent ? "idempotent" : "naive") + " consumer subscribes", nodes("client", "user", "product", "broker", "notification"),
                    "client", "user", "The channel is SIMULATED (not Kafka or RabbitMQ).", null, () -> {
                        long[] r = seed(ctx, bus);
                        ref[0] = r[0];
                        ref[1] = r[1];
                        ctx.channel().subscribe("orders", "notification", operation);
                        return List.of(ev("Event mechanism", CommunicationExperiments.MECHANISM), ev("Consumer", idempotent ? "remembers processed message ids" : "no deduplication"));
                    });

            rec.live("place", "The order and its outbox row commit together", nodes("client", "order", "order-db"), "client", "order",
                    "One order, one outbox row, message id ob-<order id>.", CodeRefIds.MICRO_OUTBOX_WRITE, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.PLACE_WITH_OUTBOX, new OutboxOrder(ref[0], ref[1], 1, false), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Outbox rows PENDING " + Lab.INSPECTOR_NOTE, Lab.outbox(ctx, "PENDING")));
                    });

            rec.live("relay-crashes", "The relay publishes, then crashes before marking the row", nodes("order", "order-db", "broker"), "order", "broker",
                    "The event reached the channel, but the relay failed before it recorded that. The row is still PENDING.", CodeRefIds.MICRO_OUTBOX_RELAY, () -> {
                        bus.startRequest();
                        CallResult<?> r = bus.call(null, OrderContract.RELAY_OUTBOX, new Relay(true), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Events published", ctx.channel().published()),
                                ev("Outbox rows PENDING " + Lab.INSPECTOR_NOTE, Lab.outbox(ctx, "PENDING")));
                    });

            rec.live("relay-again", "The relay runs again and publishes the same row again", nodes("order", "order-db", "broker"), "order", "broker",
                    "It cannot know the first publication happened, so it publishes again. The event carries the SAME message id.", CodeRefIds.MICRO_OUTBOX_RELAY, () -> {
                        bus.startRequest();
                        CallResult<Relayed> r = bus.call(null, OrderContract.RELAY_OUTBOX, new Relay(false), Lab.SETUP);
                        return List.of(ev("Caller sees", Lab.outcome(r)), ev("Events published", ctx.channel().published()),
                                ev("Outbox rows PUBLISHED " + Lab.INSPECTOR_NOTE, Lab.outbox(ctx, "PUBLISHED")));
                    });

            rec.live("deliver", "The consumer receives the event twice", nodes("broker", "notification", "notification-db"), "broker", "notification",
                    "Both copies are delivered. Whether that matters depends on the consumer.", CodeRefIds.MICRO_IDEMPOTENT_CONSUMER, () -> {
                        bus.startRequest();
                        List<EventChannel.Attempt> attempts = ctx.channel().deliverPending("notification", CONSUMER_TIMEOUT);
                        attempts.forEach(a -> ids.add(a.messageId()));
                        deliveries[0] = attempts.size();
                        return List.of(ev("Deliveries", attempts.size()), ev("Distinct message ids", ids.size()), Lab.notificationsEv(ctx),
                                ev("Processed message ids recorded " + Lab.INSPECTOR_NOTE, Lab.processed(ctx)));
                    });

            rec.infer("at-least-once", "At-least-once, not exactly-once", nodes("broker", "notification"),
                    "Calculated from the deliveries and the effects.",
                    List.of(ev("Times one outbox row was published", deliveries[0]), ev("Notifications for one order", Lab.notifications(ctx)),
                            ev("The side effect happened once", Lab.notifications(ctx) == 1),
                            ev("Delivery guarantee", "at-least-once; exactly-once delivery is not provided"),
                            ev("What made the effect happen once", idempotent ? "the consumer recorded the message id in the same local transaction as its effect"
                                    : "nothing: the consumer has no deduplication")));

            rec.concept("effect-not-delivery", "Exactly-once EFFECTS, not exactly-once delivery", nodes("broker", "notification"),
                    "A channel and a relay can promise that every event is delivered at least once, not exactly once. Systems that appear to do exactly-once work "
                            + "by making the consumer idempotent, so a repeated delivery is harmless. The outbox gives you the first guarantee; the idempotent consumer gives "
                            + "you the second.");
        }
    }
}
