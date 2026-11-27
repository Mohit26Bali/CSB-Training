/** Provided to the learner: the only exception the retry executor is expected to retry. */
public class TransientException extends Exception {
    public TransientException(String message) {
        super(message);
    }
}
