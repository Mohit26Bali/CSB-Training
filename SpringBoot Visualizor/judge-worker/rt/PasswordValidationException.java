/** Provided to the learner: a checked exception: the password breaks a rule. */
public class PasswordValidationException extends Exception {
    public PasswordValidationException(String message) {
        super(message);
    }
}
