/** Provided to the learner: an event row of the outbox. Ids increase in the order the events were written. */
public record OutboxEvent(long id, String payload) {
}
