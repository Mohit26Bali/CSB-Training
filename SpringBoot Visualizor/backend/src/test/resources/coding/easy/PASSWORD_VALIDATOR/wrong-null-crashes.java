public class PasswordValidator {

    public String firstError(String password) {
        if (password.isEmpty()) {
            return "empty";
        }
        if (password.length() < 8) {
            return "too short";
        }
        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean special = false;
        for (char c : password.toCharArray()) {
            if (c >= 'A' && c <= 'Z') {
                upper = true;
            } else if (c >= 'a' && c <= 'z') {
                lower = true;
            } else if (c >= '0' && c <= '9') {
                digit = true;
            } else {
                special = true;
            }
        }
        if (!upper) {
            return "no uppercase";
        }
        if (!lower) {
            return "no lowercase";
        }
        if (!digit) {
            return "no digit";
        }
        if (!special) {
            return "no special character";
        }
        return "OK";
    }

    public void validate(String password) throws PasswordValidationException {
        String error = firstError(password);
        if (!error.equals("OK")) {
            throw new PasswordValidationException(error);
        }
    }
}
