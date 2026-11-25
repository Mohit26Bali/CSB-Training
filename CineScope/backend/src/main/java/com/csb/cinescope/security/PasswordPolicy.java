package com.csb.cinescope.security;

import java.util.Locale;
import java.util.Set;

/**
 * The password strength rules, in one place. Used by {@link StrongPasswordValidator} (the {@code @StrongPassword} check on
 * the registration request, run by Spring before the controller body) and by {@code service/AuthService#register} for the
 * one rule that needs the rest of the form (the password must not contain the person's username or email name).
 * The frontend repeats the same rules in lib/password.ts so people see what is missing while typing; THIS class is the
 * authority.
 *
 * <p>Rules: 8 to 20 characters (a product choice; BCrypt itself could use 72; the length itself is checked by {@code @Size}); a lowercase letter,
 * an uppercase letter, a digit and a symbol; the same character not more than three times in a row; and not a common password.
 * "Common" is judged on the letters only, both as typed and after undoing the usual look-alike swaps (@ for a, 0 for o, $ for s ...),
 * so "Password1!", "P@ssw0rd" and "Welcome@2024" are caught as well as "password". Login is NOT checked against these rules, so accounts created
 * before the rules existed can still sign in.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 20;

    private static final int MAX_REPEAT = 3;
    private static final int MIN_IDENTITY_LENGTH = 3;

    /** Letters only, lower case. Keep in step with COMMON in the frontend's lib/password.ts. */
    private static final Set<String> COMMON = Set.of(
            "abc", "abcd", "abcde", "abcdef", "abcdefg", "abcdefgh", "access", "admin", "administrator", "andrew",
            "asdf", "asdfgh", "asdfghjkl", "ashley", "bank", "banking", "baseball", "batman", "bollywood", "buster",
            "changeme", "charlie", "cheese", "chocolate", "cinescope", "computer", "crestline", "cricket", "daniel",
            "default", "dragon", "family", "flower", "football", "freedom", "ganesh", "george", "google", "guest",
            "harley", "hello", "helloworld", "hockey", "hunter", "iloveu", "iloveyou", "india", "internet",
            "jennifer", "jessica", "jordan", "killer", "krishna", "letmein", "login", "lovely", "loveyou", "master",
            "michael", "mobile", "monkey", "movie", "movies", "mustang", "mypass", "mypassword", "naruto",
            "newpassword", "ninja", "pass", "passcode", "passwd", "password", "pepper", "pokemon", "princess",
            "qazwsx", "qwerty", "qwertyu", "qwertyui", "qwertyuiop", "rahul", "ranger", "robert", "root", "sachin",
            "samsung", "secret", "shadow", "shiva", "soccer", "spring", "starwars", "summer", "sunshine", "superman",
            "test", "testing", "thomas", "toor", "trustno", "user", "welcome", "whatever", "winter", "yourpassword",
            "zxcvbn", "zxcvbnm");

    private PasswordPolicy() {
    }

    /**
     * The first thing wrong with the password on its own, as a message for the user, or null when it is strong enough.
     * Length is not judged here (see the class comment), so an empty or over-long value gives null.
     */
    public static String problem(String password) {
        if (password == null || password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            return null;
        }
        java.util.List<String> missing = new java.util.ArrayList<>();
        if (password.chars().noneMatch(Character::isLowerCase)) {
            missing.add("a lowercase letter");
        }
        if (password.chars().noneMatch(Character::isUpperCase)) {
            missing.add("an uppercase letter");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            missing.add("a digit");
        }
        if (password.chars().noneMatch(c -> !Character.isLetterOrDigit(c) && !Character.isWhitespace(c))) {
            missing.add("a symbol such as ! @ # or -");
        }
        if (!missing.isEmpty()) {
            return "password must include " + join(missing);
        }
        if (hasLongRepeat(password)) {
            return "password must not repeat the same character more than " + MAX_REPEAT + " times in a row";
        }
        if (isCommon(password)) {
            return "password is too common; choose something harder to guess";
        }
        return null;
    }

    /** The rule that needs the form: the password must not contain the username or the name part of the email. */
    public static String identityProblem(String password, String username, String email) {
        String lower = password.toLowerCase(Locale.ROOT);
        if (username != null && username.trim().length() >= MIN_IDENTITY_LENGTH && lower.contains(username.trim().toLowerCase(Locale.ROOT))) {
            return "password must not contain your username";
        }
        if (email != null) {
            String name = email.trim().toLowerCase(Locale.ROOT);
            int at = name.indexOf('@');
            name = at > 0 ? name.substring(0, at) : name;
            if (name.length() >= MIN_IDENTITY_LENGTH && lower.contains(name)) {
                return "password must not contain your email name";
            }
        }
        return null;
    }

    static boolean isCommon(String password) {
        String lower = password.toLowerCase(Locale.ROOT);
        return COMMON.contains(lower) || COMMON.contains(lettersOf(lower, false)) || COMMON.contains(lettersOf(lower, true));
    }

    /** Only the letters of the text; with {@code undoSwaps} the look-alikes (@ 4 for a, 3 e, 0 o, $ 5 s, 7 t) count as letters. */
    private static String lettersOf(String lower, boolean undoSwaps) {
        StringBuilder letters = new StringBuilder();
        for (char c : lower.toCharArray()) {
            char use = c;
            if (undoSwaps) {
                use = switch (c) {
                    case '@', '4' -> 'a';
                    case '3' -> 'e';
                    case '0' -> 'o';
                    case '$', '5' -> 's';
                    case '7' -> 't';
                    default -> c;
                };
            }
            if (use >= 'a' && use <= 'z') {
                letters.append(use);
            }
        }
        return letters.toString();
    }

    private static boolean hasLongRepeat(String s) {
        int run = 1;
        for (int i = 1; i < s.length(); i++) {
            run = s.charAt(i) == s.charAt(i - 1) ? run + 1 : 1;
            if (run > MAX_REPEAT) {
                return true;
            }
        }
        return false;
    }

    private static String join(java.util.List<String> parts) {
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return String.join(", ", parts.subList(0, parts.size() - 1)) + " and " + parts.get(parts.size() - 1);
    }
}
