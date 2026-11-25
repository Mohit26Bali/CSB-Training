package shop;

import java.text.Normalizer;
import java.util.Locale;

public final class TextUtils {
    private TextUtils() { }

    /** "  Hello, World!  " becomes "hello-world". */
    public static String slugify(String text) {
        if (text == null) throw new IllegalArgumentException("text is required");
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return plain.toLowerCase(Locale.ROOT).trim().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
    }

    public static boolean isPalindrome(String text) {
        if (text == null) return false;
        String s = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return new StringBuilder(s).reverse().toString().equals(s);
    }
}
