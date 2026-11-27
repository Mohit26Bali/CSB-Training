/** Provided to the learner: the channel that delivers an event to consumers. May fail. */
public interface EventPublisher {
    void publish(OutboxEvent event) throws Exception;
}
