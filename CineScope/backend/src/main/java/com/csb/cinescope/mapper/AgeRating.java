package com.csb.cinescope.mapper;

import java.util.Locale;
import java.util.Set;

/**
 * What an age rating from TMDB means for "is it for the family?". One place decides, so the title page's verdict and the Tonight
 * page's family-safe filter can never disagree. Used by {@code mapper/TitleMapper} (the {@code familyFriendly} flag of a title) and
 * {@code service/RecommendationService} (the filter).
 *
 * <p>The rule is deliberately cautious: only ratings that mean "suitable for everyone" count. India's {@code U}, the US film ratings
 * {@code G} and {@code PG}, and the US television ratings {@code TV-Y}, {@code TV-Y7}, {@code TV-G} and {@code TV-PG}. Anything that
 * asks for a parent's judgement or an older viewer ({@code UA}, {@code PG-13}, {@code TV-14}, {@code A}, {@code R}, {@code TV-MA},
 * {@code NC-17}), and anything with no rating at all, is not counted, because "we do not know" must not pass as "safe".
 * Tested by {@code AgeRatingTest}.
 */
public final class AgeRating {

    private static final Set<String> ALL_AGES = Set.of("U", "G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "0+", "ALL", "ALL AGES");

    private AgeRating() {
    }

    /** True only for a rating that means suitable for everyone, including children (see the class comment). */
    public static boolean isFamilyFriendly(String certification) {
        return certification != null && ALL_AGES.contains(certification.trim().toUpperCase(Locale.ROOT));
    }
}
