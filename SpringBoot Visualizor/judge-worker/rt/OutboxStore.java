import java.util.List;

/** Provided to the learner: the outbox table. */
public interface OutboxStore {
    /** Up to {@code limit} events that are not yet marked published, oldest (lowest id) first. */
    List<OutboxEvent> fetchUnpublished(int limit);

    /** Marks one event published. May fail. */
    void markPublished(long id);
}
