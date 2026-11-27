import java.util.ArrayList;
import java.util.List;

public class RequestValidator {

    public List<String> validate(String name, String email, int age) {
        List<String> errors = new ArrayList<>();
        if (name == null || name.trim().isEmpty()) {
            errors.add("name:must not be blank");
        } else if (name.length() > 50) {
            errors.add("name:too long");
        }
        if (email == null || email.trim().isEmpty()) {
            errors.add("email:must not be blank");
        } else if (!validEmail(email)) {
            errors.add("email:invalid format");
        }
        if (age < 18) {
            errors.add("age:must be at least 18");
        } else if (age > 120) {
            errors.add("age:must be at most 120");
        }
        return errors;
    }

    private boolean validEmail(String e) {
        int at = e.indexOf('@');
        if (at < 0) {
            return false;
        }
        String domain = e.substring(at + 1);
        for (int i = 1; i < domain.length() - 1; i++) {
            if (domain.charAt(i) == '.') {
                return true;
            }
        }
        return true;
    }
}
