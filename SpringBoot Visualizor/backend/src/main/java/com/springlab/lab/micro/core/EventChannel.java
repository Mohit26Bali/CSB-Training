package com.springlab.lab.micro.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A SIMULATED message channel for ONE run: an in-memory list of events and explicit delivery to subscribed consumers. It is NOT
 * Kafka, RabbitMQ or any real broker: there is no persistence, no partitions, no consumer groups and no network. It exists to make
 * the difference between a synchronous call (the caller waits) and an event (the sender does not) observable and deterministic.
 *
 * <p>Delivery is explicit: events stay pending until delivery is requested, so the effect of "publish now, consume later" can be shown
 * step by step. A delivery to an unavailable consumer fails and the event stays pending (at-least-once delivery); {@link #redeliver}
 * simulates the duplicate delivery that at-least-once systems can produce. Every published entry is distinct even when two entries carry
 * the same {@code messageId} (an outbox relay that publishes a row twice), and each is delivered separately: the CONSUMER must recognise
 * the duplicate. Nothing here claims exactly-once delivery.
 */
public final class EventChannel {

    public record Attempt(String messageId, String consumer, Outcome outcome, String code, boolean duplicate) {
    }

    private record Subscription(String topic, String consumer, Operation<EventMessage, ?> operation) {
    }

    private record Entry(int seq, EventMessage message, String correlation) {
    }

    private final ServiceBus bus;
    private final List<Entry> log = new ArrayList<>();
    private final List<Subscription> subscriptions = new ArrayList<>();
    private final Map<String, Set<Integer>> acknowledged = new HashMap<>();
    private int autoIds;
    private boolean propagateCorrelation;

    public EventChannel(ServiceBus bus) {
        this.bus = bus;
    }

    /** When true the correlation id of the publishing request travels inside the event, so the consumer continues the same trace. */
    public void propagateCorrelation(boolean propagate) {
        this.propagateCorrelation = propagate;
    }

    public void subscribe(String topic, String consumer, Operation<EventMessage, ?> operation) {
        subscriptions.add(new Subscription(topic, consumer, operation));
    }

    /** Appends an event to the channel. The caller does not wait for any consumer. */
    public EventMessage publish(String topic, String type, long orderRef) {
        return publish(topic, type, orderRef, null, Map.of());
    }

    /** {@code messageId} may be null (a fresh id is assigned) or a stable id chosen by the publisher, such as an outbox row. */
    public EventMessage publish(String topic, String type, long orderRef, String messageId, Map<String, String> data) {
        EventMessage message = new EventMessage(messageId != null ? messageId : "msg-" + (++autoIds), topic, type, orderRef, Map.copyOf(data));
        log.add(new Entry(log.size() + 1, message, bus.currentCorrelation()));
        return message;
    }

    public int published() {
        return log.size();
    }

    public int pending(String consumer) {
        int count = 0;
        for (Subscription s : subscriptions) {
            if (s.consumer.equals(consumer)) {
                for (Entry e : log) {
                    if (e.message.topic().equals(s.topic) && !acknowledged.getOrDefault(consumer, Set.of()).contains(e.seq)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    /** Delivers every event the consumer has not acknowledged yet, once each. A failed delivery leaves the event pending. */
    public List<Attempt> deliverPending(String consumer, CallOptions options) {
        List<Attempt> attempts = new ArrayList<>();
        for (Subscription s : subscriptions) {
            if (!s.consumer.equals(consumer)) {
                continue;
            }
            for (Entry e : List.copyOf(log)) {
                if (e.message.topic().equals(s.topic) && !acknowledged.computeIfAbsent(consumer, k -> new HashSet<>()).contains(e.seq)) {
                    attempts.add(attempt(s, e, options, false));
                }
            }
        }
        return attempts;
    }

    /**
     * Delivers pending events to every consumer, pass after pass, until a whole pass makes no progress or {@code maxPasses} is reached.
     * Events published by consumers while they handle an event (a chain of services reacting to each other) are delivered in later passes.
     */
    public List<Attempt> deliverAll(CallOptions options, int maxPasses) {
        Set<String> consumers = new LinkedHashSet<>();
        subscriptions.forEach(s -> consumers.add(s.consumer));
        List<Attempt> all = new ArrayList<>();
        for (int pass = 0; pass < maxPasses; pass++) {
            boolean progress = false;
            for (String consumer : consumers) {
                for (Attempt a : deliverPending(consumer, options)) {
                    all.add(a);
                    progress |= a.outcome() == Outcome.SUCCESS;
                }
            }
            if (!progress) {
                break;
            }
        }
        return all;
    }

    /** Delivers an event AGAIN, even though the consumer already acknowledged it: the duplicate of an at-least-once channel. */
    public Attempt redeliver(String consumer, String messageId, CallOptions options) {
        for (Subscription s : subscriptions) {
            if (s.consumer.equals(consumer)) {
                for (Entry e : log) {
                    if (e.message.messageId().equals(messageId) && e.message.topic().equals(s.topic)) {
                        return attempt(s, e, options, true);
                    }
                }
            }
        }
        throw new IllegalArgumentException("No such event for this consumer");
    }

    private Attempt attempt(Subscription s, Entry e, CallOptions options, boolean duplicate) {
        String previous = bus.currentCorrelation();
        if (propagateCorrelation) {
            bus.useCorrelation(e.correlation);
        }
        CallResult<?> result;
        try {
            result = deliver(s.operation, e.message, options);
        } finally {
            if (propagateCorrelation) {
                bus.useCorrelation(previous);
            }
        }
        if (result.outcome() == Outcome.SUCCESS) {
            acknowledged.computeIfAbsent(s.consumer, k -> new HashSet<>()).add(e.seq);
        }
        return new Attempt(e.message.messageId(), s.consumer, result.outcome(), result.code(), duplicate);
    }

    private <R> CallResult<R> deliver(Operation<EventMessage, ?> operation, EventMessage message, CallOptions options) {
        @SuppressWarnings("unchecked")
        Operation<EventMessage, R> typed = (Operation<EventMessage, R>) operation;
        return bus.deliver(typed, message, options);
    }
}
