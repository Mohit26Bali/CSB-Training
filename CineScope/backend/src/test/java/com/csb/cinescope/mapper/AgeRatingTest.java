package com.csb.cinescope.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** What counts as suitable for the family: only ratings that mean everyone, and "unknown" never passes as safe. */
class AgeRatingTest {

    @ParameterizedTest
    @ValueSource(strings = { "U", "G", "PG", "TV-Y", "TV-Y7", "TV-Y7-FV", "TV-G", "TV-PG", "0+", "ALL" })
    void ratingsThatMeanEveryoneAreFamilyFriendly(String rating) {
        assertTrue(AgeRating.isFamilyFriendly(rating), rating);
    }

    @ParameterizedTest
    @ValueSource(strings = { "UA", "UA 7+", "UA 13+", "UA 16+", "A", "S", "PG-13", "R", "NC-17", "TV-14", "TV-MA", "12", "12A", "15", "18", "NR", "X" })
    void ratingsThatNeedAParentsJudgementOrAnOlderViewerAreNot(String rating) {
        assertFalse(AgeRating.isFamilyFriendly(rating), rating);
    }

    @Test
    void noRatingIsNotSafe() {
        assertFalse(AgeRating.isFamilyFriendly(null));
        assertFalse(AgeRating.isFamilyFriendly(""));
        assertFalse(AgeRating.isFamilyFriendly("   "));
    }

    @Test
    void caseAndSpacesDoNotMatter() {
        assertTrue(AgeRating.isFamilyFriendly(" u "));
        assertTrue(AgeRating.isFamilyFriendly("tv-g"));
        assertFalse(AgeRating.isFamilyFriendly("pg-13"));
    }
}
