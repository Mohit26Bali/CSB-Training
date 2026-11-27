/** Provided to the learner: a checked exception: a withdrawal asks for more than the balance. */
public class InsufficientBalanceException extends Exception {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
