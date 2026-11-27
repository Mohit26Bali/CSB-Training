/** Provided to the learner: thrown when a request with the same idempotency key is already being processed. */
public class RequestInProgressException extends RuntimeException {
    public RequestInProgressException(String message) {
        super(message);
    }
}
