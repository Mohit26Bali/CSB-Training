/** Provided to the learner: a product with that id already exists. */
public class DuplicateProductException extends RuntimeException {
    public DuplicateProductException(String message) {
        super(message);
    }
}
