/** Provided to the learner: no bean of the requested type is registered. */
public class NoSuchBeanException extends RuntimeException {
    public NoSuchBeanException(String message) {
        super(message);
    }
}
