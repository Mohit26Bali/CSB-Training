package com.csb.cinescope.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.entity.CommentStatus;
import com.csb.cinescope.entity.Sentiment;
import com.csb.cinescope.service.CommentProcessor.Result;
import com.csb.cinescope.service.CommentProcessor.Step;

/**
 * Unit tests of the comment-processing pipeline ({@link CommentProcessor}), stage by stage, with plain strings: cleaning, spam
 * check, profanity filter, sentiment analysis (including negation, intensifiers and the exact scores) and the final decision.
 */
class CommentProcessorTest {

    CommentProcessor processor = new CommentProcessor();

    private static Step step(Result r, String name) {
        return r.steps().stream().filter(s -> s.name().equals(name)).findFirst().orElseThrow();
    }

    // ---- shape ----

    @Test
    void everyCommentGoesThroughTheSameFiveStagesInOrder() {
        Result r = processor.process("A perfectly ordinary comment about the film.");

        assertEquals(List.of("Cleaned", "Spam check", "Profanity filter", "Sentiment analysis", "Decision"), r.steps().stream().map(Step::name).toList());
        assertEquals(CommentStatus.PUBLISHED, r.status());
        assertEquals("Published", step(r, "Decision").detail());
    }

    @Test
    void theStepsSurviveBeingStoredAndReadBack() {
        Result r = processor.process("Absolutely brilliant   film!!");

        List<Step> back = CommentProcessor.decode(r.encodedSteps());

        assertEquals(r.steps(), back);
        assertEquals(List.of(), CommentProcessor.decode(null));
        assertEquals(List.of(), CommentProcessor.decode("garbage without pipes"));
    }

    // ---- cleaning ----

    @Test
    void extraSpacesAreSqueezedAndStretchedWordsShortened() {
        Result r = processor.process("  this   was   sooooo   good  ");

        assertEquals("this was sooo good", r.text());
        assertEquals("CHANGED", step(r, "Cleaned").outcome());
        assertTrue(step(r, "Cleaned").detail().contains("shortened 1 stretched word"));
    }

    @Test
    void aTidyCommentIsLeftAlone() {
        Result r = processor.process("A tidy comment.");

        assertEquals("A tidy comment.", r.text());
        assertEquals("PASSED", step(r, "Cleaned").outcome());
    }

    // ---- spam ----

    @Test
    void threeLinksHoldTheCommentForAModerator() {
        Result r = processor.process("Watch free http://a.example and www.b.example and https://c.example now");

        assertEquals(CommentStatus.PENDING, r.status());
        assertEquals("HELD", step(r, "Spam check").outcome());
        assertEquals("3 links found: held for a moderator", step(r, "Spam check").detail());
        assertEquals("HELD", step(r, "Decision").outcome());
    }

    @Test
    void oneOrTwoLinksAreFine() {
        Result r = processor.process("Details here http://example.com/film and here www.example.org");

        assertEquals(CommentStatus.PUBLISHED, r.status());
        assertEquals("PASSED", step(r, "Spam check").outcome());
        assertEquals("2 links, looks fine", step(r, "Spam check").detail());
    }

    @Test
    void shoutingAndExclamationStormsAreFlaggedButStillPublished() {
        Result shout = processor.process("THIS MOVIE IS ABSOLUTELY AMAZING");
        Result storm = processor.process("good good good!!!!!! really!");

        assertEquals("FLAGGED", step(shout, "Spam check").outcome());
        assertEquals("Mostly capital letters (shouting)", step(shout, "Spam check").detail());
        assertEquals(CommentStatus.PUBLISHED, shout.status());
        assertEquals("FLAGGED", step(storm, "Spam check").outcome());
        assertEquals(CommentStatus.PUBLISHED, storm.status());
    }

    // ---- profanity ----

    @Test
    void mildBadLanguageIsMaskedAndPublished() {
        Result r = processor.process("That was Damn good, not stupid at all");

        assertEquals("That was D*** good, not s***** at all", r.text());
        assertEquals("CHANGED", step(r, "Profanity filter").outcome());
        assertEquals("Masked 2 words", step(r, "Profanity filter").detail());
        assertEquals(CommentStatus.PUBLISHED, r.status());
    }

    @Test
    void strongLanguageIsMaskedAndHoldsTheComment() {
        Result r = processor.process("What a fucking awful ending");

        assertEquals("What a f****** awful ending", r.text()); // first letter kept, the other six masked
        assertEquals("HELD", step(r, "Profanity filter").outcome());
        assertEquals(CommentStatus.PENDING, r.status());
    }

    @Test
    void onlyWholeWordsAreMasked() {
        Result r = processor.process("A damnation of a classic, and the classes were fine");

        assertEquals("A damnation of a classic, and the classes were fine", r.text());
        assertEquals("PASSED", step(r, "Profanity filter").outcome());
    }

    @Test
    void maskedWordsStillCountTowardsTheSentiment() {
        Result r = processor.process("stupid and boring");

        assertEquals("s***** and boring", r.text());
        assertEquals(Sentiment.NEGATIVE, r.sentiment());
        assertEquals(-0.5, r.sentimentScore());
    }

    // ---- sentiment ----

    @Test
    void oneStrongPositiveWordGivesPlusPointFour() {
        Result r = processor.process("Amazing");

        assertEquals(0.4, r.sentimentScore());
        assertEquals(Sentiment.POSITIVE, r.sentiment());
        assertEquals("Positive (+0.40) from 1 emotion word", step(r, "Sentiment analysis").detail());
    }

    @Test
    void aPlainNegativeWordGivesMinusPointThirtyThree() {
        Result r = processor.process("Boring");

        assertEquals(-0.33, r.sentimentScore());
        assertEquals(Sentiment.NEGATIVE, r.sentiment());
    }

    @Test
    void negationTurnsAWordRoundAndWeakensIt() {
        assertEquals(-0.2, processor.process("not good").sentimentScore()); // -(1 * 0.75) / (0.75 + 3)
        assertEquals(Sentiment.NEGATIVE, processor.process("This is not good").sentiment());
        assertEquals(0.27, processor.process("not bad").sentimentScore()); // a negated negative is positive
        assertEquals(Sentiment.POSITIVE, processor.process("It was never boring").sentiment());
        assertEquals(Sentiment.NEGATIVE, processor.process("It isn’t good").sentiment()); // curly apostrophe, n't ending
        assertEquals(Sentiment.NEGATIVE, processor.process("It wasn't good").sentiment());
    }

    @Test
    void negationOnlyReachesTheNextEmotionWordAndStopsAtPunctuationAndBut() {
        // before the fix "not" also flipped "boring" three words later
        assertEquals(-0.5, processor.process("Not good, really boring").sentimentScore()); // -0.75 and -2.25
        assertEquals(Sentiment.NEGATIVE, processor.process("Not good, really boring").sentiment());
        // "but" ends the negation: -0.75 for "not good", then +1 for the plain "good" -> 0.25 -> 0.08, neutral
        assertEquals(0.08, processor.process("not good but good").sentimentScore());
        assertEquals(Sentiment.NEUTRAL, processor.process("not good but good").sentiment());
    }

    @Test
    void anEmotionWordUsesUpTheNegationSoItDoesNotLeakFurther() {
        // "never boring" is positive; the following "slow" must still be judged on its own (negative)
        // +1.125 (a negated -1.5) and then -0.75 for "slow", judged on its own: 0.375 / 3.375 = 0.11
        assertEquals(0.11, processor.process("never boring and slow").sentimentScore());
    }

    @Test
    void intensifiersStrengthenAndDiminishersSoften() {
        assertEquals(0.5, processor.process("really amazing").sentimentScore()); // 2 * 1.5 = 3 -> 3/6
        assertEquals(-0.2, processor.process("slightly boring").sentimentScore()); // -1.5 * 0.5 = -0.75 -> -0.2
        assertEquals(0.6, processor.process("I loved it, absolutely brilliant").sentimentScore()); // 1.5 + 2*1.5 = 4.5 -> 4.5/7.5
    }

    @Test
    void mixedFeelingsAddUp() {
        Result r = processor.process("Good acting but boring and predictable");

        assertEquals(Sentiment.NEGATIVE, r.sentiment()); // 1 - 1.5 - 1 = -1.5 -> -0.33
        assertEquals(-0.33, r.sentimentScore());
        assertTrue(step(r, "Sentiment analysis").detail().endsWith("from 3 emotion words"));
    }

    @Test
    void aTextWithoutEmotionWordsIsNeutral() {
        Result r = processor.process("The film was released in 2010 and runs 148 minutes");

        assertEquals(Sentiment.NEUTRAL, r.sentiment());
        assertEquals(0.0, r.sentimentScore());
        assertEquals("Neutral (0.00): no emotion words found", step(r, "Sentiment analysis").detail());
    }

    @Test
    void thePerWordWeightNeverPushesTheScoreToOne() {
        Result r = processor.process("amazing brilliant masterpiece perfect flawless stunning superb excellent fantastic wonderful");

        assertTrue(r.sentimentScore() < 1.0 && r.sentimentScore() > 0.8, "score was " + r.sentimentScore());
        assertFalse(Double.isNaN(r.sentimentScore()));
    }

    @Test
    void theScoreIsRoundedToTwoDecimals() {
        double score = processor.process("good but slow and a bit weak").sentimentScore();

        assertEquals(Math.round(score * 100.0) / 100.0, score);
    }
}
