package com.csb.cinescope.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.csb.cinescope.entity.CommentStatus;
import com.csb.cinescope.entity.Sentiment;

/**
 * The processing pipeline every comment goes through before it is shown. A pure piece of logic (no database, no network), so
 * every rule can be unit-tested with plain strings. Called by {@code service/CommentService} on each new or edited comment; the
 * resulting steps are stored with the comment and shown in the UI ("How was this processed?").
 *
 * <p>The stages, in order:
 * <ol>
 *   <li><b>Cleaned</b> - extra spaces are squeezed and stretched letters ("sooooo") are shortened to three.</li>
 *   <li><b>Spam check</b> - three or more links hold the comment for a moderator; shouting (mostly capitals) or a storm of
 *       exclamation marks is flagged but allowed.</li>
 *   <li><b>Profanity filter</b> - mild bad words are masked ("d***"); strong language is masked AND holds the comment for a
 *       moderator.</li>
 *   <li><b>Sentiment analysis</b> - a small word-list method: each emotion word has a weight, a "not" in front turns it round,
 *       "very" strengthens and "slightly" softens it; the total is squeezed into -1 to +1 and labelled positive, negative
 *       or neutral.</li>
 *   <li><b>Decision</b> - published, or held for review if any stage asked for that.</li>
 * </ol>
 * The word lists are deliberately short and in plain sight: a real product would use a larger list or a trained model, but
 * this is enough to explain and to test every rule. The outcome of each stage is one of PASSED, CHANGED (the text was
 * altered), FLAGGED (noted, still published) or HELD (a moderator must decide).
 */
@Component
public class CommentProcessor {

    /** One stage of the pipeline as shown to the user. */
    public record Step(String name, String outcome, String detail) {
    }

    /** Everything the pipeline decided about one comment. */
    public record Result(String text, CommentStatus status, Sentiment sentiment, double sentimentScore, List<Step> steps) {

        /** The steps as the lines stored in {@code comments.processing}: "name|OUTCOME|detail". */
        public String encodedSteps() {
            return encode(steps);
        }
    }

    private static final Pattern STRETCH = Pattern.compile("(\\p{L})\\1{3,}");
    private static final Pattern LINK = Pattern.compile("(?i)\\b(?:https?://|www\\.)\\S+");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}']+|[,.;:!?()\\-]");
    private static final Set<String> BOUNDARIES = Set.of(",", ".", ";", ":", "!", "?", "(", ")", "-", "but", "however", "though", "although", "yet");
    private static final int WINDOW = 3;
    private static final int LINKS_BEFORE_HOLD = 3;
    private static final int EXCLAMATIONS_BEFORE_FLAG = 6;
    private static final double LABEL_THRESHOLD = 0.12;

    private static final Set<String> MILD_WORDS = Set.of("damn", "crap", "idiot", "moron", "stupid", "wtf");
    private static final Set<String> STRONG_WORDS = Set.of("shit", "fuck", "fucking", "bitch", "asshole", "bastard");
    private static final Pattern PROFANITY = Pattern.compile("(?i)\\b(" + String.join("|", MILD_WORDS) + "|" + String.join("|", STRONG_WORDS) + ")\\b");

    /** Emotion words and their weight: about 1 for a plain word, 2 for a strong one. Words that look like profanity count too. */
    static final Map<String, Double> LEXICON = lexicon();

    private static final Set<String> NEGATORS = Set.of("not", "no", "never", "hardly", "barely", "without", "cannot", "nothing", "neither", "nor");
    private static final Set<String> INTENSIFIERS = Set.of("very", "really", "extremely", "absolutely", "incredibly", "totally", "utterly", "so", "truly", "insanely", "super", "highly");
    private static final Set<String> DIMINISHERS = Set.of("slightly", "somewhat", "bit", "little", "kinda", "fairly", "rather", "mildly");

    private static Map<String, Double> lexicon() {
        Map<String, Double> words = new java.util.HashMap<>();
        for (String w : "amazing brilliant masterpiece perfect flawless stunning superb excellent fantastic wonderful awesome outstanding incredible phenomenal breathtaking brilliantly".split(" ")) {
            words.put(w, 2.0);
        }
        for (String w : "favourite favorite beautiful powerful gripping touching moving hilarious loved love great best unforgettable thrilling".split(" ")) {
            words.put(w, 1.5);
        }
        for (String w : "enjoyed enjoyable fun funny good nice solid clever charming recommend recommended worth entertaining engaging memorable smart rewarding haunting".split(" ")) {
            words.put(w, 1.0);
        }
        for (String w : "awful terrible horrible worst hate hated garbage unwatchable shit fuck fucking bitch asshole bastard".split(" ")) {
            words.put(w, -2.0);
        }
        for (String w : "trash boring dull overrated disappointing disappointed disappointment waste wasted mess bad annoying cringe pointless painful rubbish tedious dumb lame crap idiot moron stupid".split(" ")) {
            words.put(w, -1.5);
        }
        for (String w : "bland mediocre forgettable predictable poor weak confusing confused messy lazy stale shallow overlong dragged damn wtf".split(" ")) {
            words.put(w, -1.0);
        }
        for (String w : "slow flat".split(" ")) {
            words.put(w, -0.75);
        }
        return Map.copyOf(words);
    }

    /** Runs the whole pipeline on a raw comment (already checked to be 2 to 1000 characters). */
    public Result process(String raw) {
        List<Step> steps = new ArrayList<>();
        boolean held = false;

        // 1. cleaning
        String text = raw.strip().replaceAll("\\s+", " ");
        int squeezed = raw.strip().length() - text.length();
        Matcher stretch = STRETCH.matcher(text);
        int stretched = 0;
        StringBuilder cleaned = new StringBuilder();
        while (stretch.find()) {
            stretched++;
            stretch.appendReplacement(cleaned, Matcher.quoteReplacement(stretch.group(1).repeat(3)));
        }
        stretch.appendTail(cleaned);
        text = cleaned.toString();
        if (squeezed > 0 || stretched > 0) {
            steps.add(new Step("Cleaned", "CHANGED", "Removed " + squeezed + " extra space" + (squeezed == 1 ? "" : "s") + " and shortened " + stretched
                    + " stretched word" + (stretched == 1 ? "" : "s")));
        } else {
            steps.add(new Step("Cleaned", "PASSED", "Nothing to tidy"));
        }

        // 2. spam check
        int links = 0;
        Matcher link = LINK.matcher(text);
        while (link.find()) {
            links++;
        }
        int letters = 0;
        int upper = 0;
        int exclamations = 0;
        for (char c : text.toCharArray()) {
            if (Character.isLetter(c)) {
                letters++;
                if (Character.isUpperCase(c)) {
                    upper++;
                }
            } else if (c == '!') {
                exclamations++;
            }
        }
        boolean shouting = letters >= 12 && upper > letters * 0.7;
        if (links >= LINKS_BEFORE_HOLD) {
            steps.add(new Step("Spam check", "HELD", links + " links found: held for a moderator"));
            held = true;
        } else if (shouting || exclamations >= EXCLAMATIONS_BEFORE_FLAG) {
            steps.add(new Step("Spam check", "FLAGGED", shouting ? "Mostly capital letters (shouting)" : exclamations + " exclamation marks"));
        } else {
            steps.add(new Step("Spam check", "PASSED", links == 0 ? "No links, no shouting" : links + " link" + (links == 1 ? "" : "s") + ", looks fine"));
        }

        // 3. profanity filter (the sentiment stage below reads the text from BEFORE masking, so bad words still count as negative)
        String unmasked = text;
        Matcher bad = PROFANITY.matcher(text);
        StringBuilder masked = new StringBuilder();
        int maskedWords = 0;
        boolean strong = false;
        while (bad.find()) {
            String word = bad.group(1);
            maskedWords++;
            strong |= STRONG_WORDS.contains(word.toLowerCase(Locale.ROOT));
            bad.appendReplacement(masked, Matcher.quoteReplacement(word.charAt(0) + "*".repeat(word.length() - 1)));
        }
        bad.appendTail(masked);
        text = masked.toString();
        if (maskedWords == 0) {
            steps.add(new Step("Profanity filter", "PASSED", "No bad language"));
        } else if (strong) {
            steps.add(new Step("Profanity filter", "HELD", "Strong language, masked " + maskedWords + " word" + (maskedWords == 1 ? "" : "s") + ": held for a moderator"));
            held = true;
        } else {
            steps.add(new Step("Profanity filter", "CHANGED", "Masked " + maskedWords + " word" + (maskedWords == 1 ? "" : "s")));
        }

        // 4. sentiment analysis
        Sentiment sentiment;
        double score;
        int emotionWords;
        double[] analysis = analyse(unmasked);
        score = Math.round(analysis[0] * 100.0) / 100.0;
        emotionWords = (int) analysis[1];
        sentiment = score >= LABEL_THRESHOLD ? Sentiment.POSITIVE : score <= -LABEL_THRESHOLD ? Sentiment.NEGATIVE : Sentiment.NEUTRAL;
        String label = sentiment.name().charAt(0) + sentiment.name().substring(1).toLowerCase(Locale.ROOT);
        steps.add(new Step("Sentiment analysis", "PASSED", emotionWords == 0
                ? "Neutral (0.00): no emotion words found"
                : String.format(Locale.ROOT, "%s (%+.2f) from %d emotion word%s", label, score, emotionWords, emotionWords == 1 ? "" : "s")));

        // 5. decision
        CommentStatus status = held ? CommentStatus.PENDING : CommentStatus.PUBLISHED;
        steps.add(new Step("Decision", held ? "HELD" : "PASSED", held ? "Held for review: only you and the moderators can see it for now" : "Published"));
        return new Result(text, status, sentiment, score, List.copyOf(steps));
    }

    /**
     * The word-list sentiment method. Returns {score, number of emotion words}. Reading left to right, a "not"-like word flips
     * the NEXT emotion word within three words (weakening it a little), a "very"-like word multiplies it by 1.5 and a
     * "slightly"-like word halves it; each effect is used up by the emotion word it reaches and also ends at punctuation or at
     * "but" / "however" / "though", so "not good, really boring" is two separate judgements. The total is squeezed into -1..+1
     * with {@code total / (|total| + 3)}: one strong word gives about 0.4, two about 0.57, and the score never reaches 1.
     */
    double[] analyse(String text) {
        double total = 0;
        int hits = 0;
        int negationLeft = 0;
        int modifierLeft = 0;
        double multiplier = 1;
        for (String token : tokenize(text)) {
            if (BOUNDARIES.contains(token)) {
                negationLeft = 0;
                modifierLeft = 0;
                multiplier = 1;
            } else if (NEGATORS.contains(token) || token.endsWith("n't")) {
                negationLeft = WINDOW;
            } else if (INTENSIFIERS.contains(token)) {
                multiplier *= 1.5;
                modifierLeft = WINDOW;
            } else if (DIMINISHERS.contains(token)) {
                multiplier *= 0.5;
                modifierLeft = WINDOW;
            } else if (LEXICON.containsKey(token)) {
                double value = LEXICON.get(token) * multiplier;
                total += negationLeft > 0 ? -value * 0.75 : value;
                hits++;
                negationLeft = 0;
                modifierLeft = 0;
                multiplier = 1;
            } else {
                negationLeft = Math.max(0, negationLeft - 1);
                if (modifierLeft > 0 && --modifierLeft == 0) {
                    multiplier = 1;
                }
            }
        }
        return new double[] { total / (Math.abs(total) + 3), hits };
    }

    /** Lower-case words and the punctuation that ends a thought, with curly apostrophes made straight. */
    static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        Matcher m = TOKEN.matcher(text.toLowerCase(Locale.ROOT).replace('\u2019', '\''));
        while (m.find()) {
            tokens.add(m.group());
        }
        return tokens;
    }

    // ---- storing and reading the steps ----

    static String encode(List<Step> steps) {
        StringBuilder out = new StringBuilder();
        for (Step s : steps) {
            if (out.length() > 0) {
                out.append('\n');
            }
            out.append(s.name()).append('|').append(s.outcome()).append('|').append(s.detail().replace('|', '/').replace('\n', ' '));
        }
        return out.toString();
    }

    /** Reads the stored lines back into steps (an empty or damaged value gives an empty list). */
    public static List<Step> decode(String stored) {
        List<Step> steps = new ArrayList<>();
        if (stored == null || stored.isBlank()) {
            return steps;
        }
        for (String line : stored.split("\n")) {
            String[] parts = line.split("\\|", 3);
            if (parts.length == 3) {
                steps.add(new Step(parts[0], parts[1], parts[2]));
            }
        }
        return steps;
    }
}
