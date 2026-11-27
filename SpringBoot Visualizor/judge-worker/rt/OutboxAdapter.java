import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An in-memory outbox table and publisher with scripted failures, and the learner's own consumer (the nested class IdempotentConsumer).
 * Every delivery the publisher accepts is also handed to the learner's consumer, as a real subscriber would receive it.
 *
 * Operations: events ID,ID,... (rows written to the outbox, payload p<id>), failpub ID N (the next N publishes of that event fail),
 * failmark ID N (the next N markPublished calls for that event fail), relay MAX, consume ID (hand one event straight to the consumer),
 * delivered, applied, unpublished.
 * Observations: relay gives the number returned; consume gives true/false; delivered is every accepted publish in order (duplicates
 * included); applied is what the consumer applied; unpublished is the rows still unmarked; each is a comma list or "-".
 */
final class OutboxAdapter extends Adapter {

    private Class<?> type;
    private Object relay;
    private Object consumer;
    private Class<?> consumerType;
    private final List<OutboxEvent> table = new ArrayList<>();
    private final java.util.Set<Long> published = new java.util.HashSet<>();
    private final Map<Long, Integer> publishFailures = new HashMap<>();
    private final Map<Long, Integer> markFailures = new HashMap<>();
    private final List<Long> delivered = new ArrayList<>();

    @Override
    String className() {
        return "OutboxRelay";
    }

    @Override
    String check(Class<?> learner) {
        try {
            learner.getConstructor(OutboxStore.class, EventPublisher.class);
            learner.getMethod("relayBatch", int.class);
            Class<?> c = consumerClass(learner);
            if (c == null) {
                throw new NoSuchMethodException();
            }
            c.getConstructor();
            c.getMethod("consume", OutboxEvent.class);
            c.getMethod("applied");
            return null;
        } catch (NoSuchMethodException e) {
            return "OutboxRelay must declare: public OutboxRelay(OutboxStore store, EventPublisher publisher), public int relayBatch(int maxEvents), and a public "
                    + "static nested class IdempotentConsumer with a public no-argument constructor, public boolean consume(OutboxEvent event) and public List<Long> applied()";
        }
    }

    private static Class<?> consumerClass(Class<?> learner) {
        for (Class<?> c : learner.getClasses()) {
            if (c.getSimpleName().equals("IdempotentConsumer")) {
                return c;
            }
        }
        return null;
    }

    @Override
    void begin(Class<?> learner) throws Exception {
        type = learner;
        table.clear();
        published.clear();
        publishFailures.clear();
        markFailures.clear();
        delivered.clear();
        consumerType = consumerClass(learner);
        consumer = consumerType.getConstructor().newInstance();
        relay = null;
    }

    private static String csv(List<?> items) {
        if (items.isEmpty()) {
            return "-";
        }
        StringBuilder b = new StringBuilder();
        for (Object o : items) {
            b.append(b.length() == 0 ? "" : ",").append(o);
        }
        return b.toString();
    }

    private OutboxStore store() {
        return new OutboxStore() {
            @Override
            public List<OutboxEvent> fetchUnpublished(int limit) {
                List<OutboxEvent> out = new ArrayList<>();
                for (OutboxEvent e : table) {
                    if (out.size() >= limit) {
                        break;
                    }
                    if (!published.contains(e.id())) {
                        out.add(e);
                    }
                }
                return out;
            }

            @Override
            public void markPublished(long id) {
                int left = markFailures.getOrDefault(id, 0);
                if (left > 0) {
                    markFailures.put(id, left - 1);
                    throw new IllegalStateException("database unavailable");
                }
                published.add(id);
            }
        };
    }

    private EventPublisher publisher() {
        return event -> {
            int left = publishFailures.getOrDefault(event.id(), 0);
            if (left > 0) {
                publishFailures.put(event.id(), left - 1);
                throw new IllegalStateException("broker unavailable");
            }
            delivered.add(event.id());
            consumerType.getMethod("consume", OutboxEvent.class).invoke(consumer, event);
        };
    }

    @Override
    String step(String[] t) throws Exception {
        switch (t[0]) {
            case "events":
                for (String id : need(t, 2).split(",")) {
                    table.add(new OutboxEvent(Long.parseLong(id), "p" + id));
                }
                return "-";
            case "failpub":
                publishFailures.put(Long.parseLong(need(t, 2)), Integer.parseInt(need(t, 3)));
                return "-";
            case "failmark":
                markFailures.put(Long.parseLong(need(t, 2)), Integer.parseInt(need(t, 3)));
                return "-";
            case "relay":
                if (relay == null) {
                    relay = type.getConstructor(OutboxStore.class, EventPublisher.class).newInstance(store(), publisher());
                }
                return String.valueOf(type.getMethod("relayBatch", int.class).invoke(relay, Integer.parseInt(need(t, 2))));
            case "consume": {
                long id = Long.parseLong(need(t, 2));
                return String.valueOf(consumerType.getMethod("consume", OutboxEvent.class).invoke(consumer, new OutboxEvent(id, "p" + id)));
            }
            case "delivered":
                return csv(delivered);
            case "applied":
                return csv((List<?>) consumerType.getMethod("applied").invoke(consumer));
            case "unpublished": {
                List<Long> ids = new ArrayList<>();
                for (OutboxEvent e : table) {
                    if (!published.contains(e.id())) {
                        ids.add(e.id());
                    }
                }
                return csv(ids);
            }
            default:
                throw new BadScript();
        }
    }
}
