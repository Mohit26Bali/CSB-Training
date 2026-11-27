import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class OutboxRelay {

    private final OutboxStore store;
    private final EventPublisher publisher;

    public OutboxRelay(OutboxStore store, EventPublisher publisher) {
        this.store = store;
        this.publisher = publisher;
    }

    public int relayBatch(int maxEvents) {
        if (maxEvents < 1) {
            throw new IllegalArgumentException("maxEvents must be at least 1");
        }
        int marked = 0;
        for (OutboxEvent event : store.fetchUnpublished(maxEvents)) {
            try {
                publisher.publish(event);
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
            try {
                store.markPublished(event.id());
            } catch (Exception failure) {
                break; // it was delivered but not recorded: it will be sent again (at least once)
            }
            marked++;
        }
        return marked;
    }

    public static final class IdempotentConsumer {
        private final Set<Long> seen = new HashSet<>();
        private final List<Long> applied = new ArrayList<>();

        public boolean consume(OutboxEvent event) {
            if (!seen.add(event.id())) {
                return false;
            }
            applied.add(event.id());
            return true;
        }

        public List<Long> applied() {
            return new ArrayList<>(applied);
        }
    }
}
