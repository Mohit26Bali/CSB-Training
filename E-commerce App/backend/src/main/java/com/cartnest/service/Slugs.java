package com.cartnest.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** URL names: "Men's Running Shoes" becomes "men-s-running-shoes". */
public final class Slugs {

    private Slugs() {
    }

    public static String of(String text) {
        String plain = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        return slug.isEmpty() ? "item" : slug;
    }

    /** The slug of the text, with -2, -3... added until it is not taken. */
    public static String unique(String text, Predicate<String> taken) {
        String base = of(text);
        String slug = base;
        for (int n = 2; taken.test(slug); n++) {
            slug = base + "-" + n;
        }
        return slug;
    }
}
