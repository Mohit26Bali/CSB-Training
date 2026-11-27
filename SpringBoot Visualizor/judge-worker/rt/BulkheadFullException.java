/** Provided to the learner: thrown by a bulkhead that has no free permit. */
public class BulkheadFullException extends RuntimeException {
    public BulkheadFullException(String message) {
        super(message);
    }
}
