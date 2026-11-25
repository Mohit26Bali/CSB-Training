package com.csb.cinescope.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.csb.cinescope.dto.CastMember;
import com.csb.cinescope.dto.QuizAnswerRequest;
import com.csb.cinescope.dto.QuizAnswerResult;
import com.csb.cinescope.dto.QuizLeaderboard;
import com.csb.cinescope.dto.QuizQuestionView;
import com.csb.cinescope.dto.QuizRoundView;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.entity.Quote;
import com.csb.cinescope.entity.QuoteCategory;
import com.csb.cinescope.entity.QuizQuestion;
import com.csb.cinescope.entity.QuizRound;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.ConflictException;
import com.csb.cinescope.exception.NotFoundException;
import com.csb.cinescope.exception.UpstreamException;
import com.csb.cinescope.repository.QuizQuestionRepository;
import com.csb.cinescope.repository.QuizRoundRepository;
import com.csb.cinescope.repository.QuoteRepository;
import com.csb.cinescope.repository.UserRepository;

/**
 * The famous-lines quiz. A round is ten questions about quotes the person has not been shown before
 * ({@code service/QuoteService} does the no-repeat choosing), each asking either which film or show a line is from, or who says
 * it, with four options. The wrong options come from the same category, and for titles from the same kind (a show among shows), so
 * the right one is not obvious.
 *
 * <p>Rules that make it fair: the correct answer is stored on the server and revealed only after a question is answered; a
 * question can be answered once; a round belongs to the person who started it; and a round counts on the leaderboard only when
 * all its questions are answered. Scoring: a right answer earns {@value #POINTS} points plus {@value #BONUS} for each right
 * answer in a row before it (up to {@value #MAX_BONUS_STEPS}); a wrong one earns nothing and ends the streak.
 *
 * <p>Two more games use the same rounds, scoring and boards but ask about a film or show instead of a quote, drawn from the titles of the
 * quotes table: CAST ("which film or show has these three actors?", from TMDB's credits) and POSTER ("name this blurred poster", from
 * TMDB's poster). Titles TMDB cannot supply a clue for are skipped. In the poster game a quick right answer earns a speed bonus
 * ({@value #FAST_BONUS} within {@value #FAST_SECONDS} seconds, {@value #SLOW_BONUS} within {@value #SLOW_SECONDS}), measured on the server
 * from the moment the page reported the question on screen ({@link #shown}); a question never reported shown earns none.
 * Called by {@code controller/QuizController}; tested by {@code QuizApiTest} and {@code QuizTitleGamesApiTest}.
 */
@Service
public class QuizService {

    public static final int QUESTIONS = 10;
    static final int OPTIONS = 4;
    static final int POINTS = 10;
    static final int BONUS = 2;
    static final int MAX_BONUS_STEPS = 5;
    private static final String ALL = "ALL";
    private static final String CAST = "CAST";
    private static final String POSTER = "POSTER";
    private static final Set<String> TITLE_GAMES = Set.of(CAST, POSTER);
    private static final Set<String> CATEGORIES = Set.of(ALL, "HOLLYWOOD", "TV", "BOLLYWOOD", "ANIME", CAST, POSTER);
    private static final int BOARD_SIZE = 10;
    static final int FAST_SECONDS = 6;
    static final int FAST_BONUS = 5;
    static final int SLOW_SECONDS = 12;
    static final int SLOW_BONUS = 2;
    /** How many titles are tried at most to find ten with a usable clue, so a TMDB outage cannot make one request fetch hundreds. */
    private static final int MAX_TITLE_ATTEMPTS = 60;
    private static final int ACTORS_SHOWN = 3;

    private final QuoteService quoteService;
    private final QuoteRepository quotes;
    private final QuizRoundRepository rounds;
    private final QuizQuestionRepository questions;
    private final UserRepository users;
    private final CatalogService catalog;
    private final ExecutorService executor;
    private final Random random;

    @Autowired
    public QuizService(QuoteService quoteService, QuoteRepository quotes, QuizRoundRepository rounds, QuizQuestionRepository questions, UserRepository users,
                       CatalogService catalog, ExecutorService catalogExecutor) {
        this(quoteService, quotes, rounds, questions, users, catalog, catalogExecutor, new Random());
    }

    QuizService(QuoteService quoteService, QuoteRepository quotes, QuizRoundRepository rounds, QuizQuestionRepository questions, UserRepository users,
                CatalogService catalog, ExecutorService executor, Random random) {
        this.quoteService = quoteService;
        this.quotes = quotes;
        this.rounds = rounds;
        this.questions = questions;
        this.users = users;
        this.catalog = catalog;
        this.executor = executor;
        this.random = random;
    }

    /** Starts a round in a category (ALL for a mix): ten questions, none answered, no answers revealed. */
    @Transactional
    public QuizRoundView start(String username, String categoryName) {
        String category = validCategory(categoryName);
        User user = userOf(username);
        if (TITLE_GAMES.contains(category)) {
            return startTitleGame(user, category);
        }
        QuoteCategory quoteCategory = ALL.equals(category) ? null : QuoteCategory.valueOf(category);

        List<Quote> picked = quoteService.next(user.getId(), quoteCategory, QUESTIONS);
        if (picked.size() < QUESTIONS) {
            throw new ConflictException("There are not enough quotes in this category to play yet");
        }
        List<Quote> pool = quoteCategory == null ? quotes.findAll() : quotes.findByCategory(quoteCategory.name());

        QuizRound round = rounds.save(new QuizRound(user.getId(), category, QUESTIONS));
        List<QuizQuestionView> views = new ArrayList<>();
        for (int i = 0; i < picked.size(); i++) {
            Quote quote = picked.get(i);
            boolean askTitle = i % 3 != 2; // two questions in three ask for the title, the rest for the speaker
            List<String> options = new ArrayList<>(wrongOptions(pool, quote, askTitle));
            int correctIndex = random.nextInt(OPTIONS);
            options.add(correctIndex, askTitle ? quote.getTitle() : quote.getSpeaker());

            String kind = askTitle ? "TITLE" : "SPEAKER";
            questions.save(new QuizQuestion(round.getId(), i + 1, quote.getId(), kind, options, correctIndex));
            String prompt = askTitle ? "Which film or show is this line from?" : "Who says this line in " + quote.getTitle() + "?";
            views.add(new QuizQuestionView(i + 1, kind, prompt, quote.getLine(), null, null, options));
        }
        return new QuizRoundView(round.getId(), category, QUESTIONS, views);
    }

    /** Checks an answer, scores it, and reveals the truth. A question can be answered only once, and only in the person's own round. */
    @Transactional
    public QuizAnswerResult answer(String username, long roundId, QuizAnswerRequest request) {
        User user = userOf(username);
        QuizRound round = rounds.findById(roundId).filter(r -> r.getUserId() == user.getId()).orElseThrow(() -> new NotFoundException("That quiz round was not found"));
        QuizQuestion question = questions.findByRoundIdAndSeq(roundId, request.seq()).orElseThrow(() -> new NotFoundException("That question was not found"));
        if (question.isAnswered()) {
            throw new ConflictException("That question has already been answered");
        }

        boolean correct = request.choice() == question.getCorrectIndex();
        int speedBonus = correct ? speedBonus(question) : 0;
        int points = correct ? POINTS + BONUS * Math.min(round.getStreak(), MAX_BONUS_STEPS) + speedBonus : 0;
        question.answer(request.choice(), points);
        round.record(correct, points);

        int answered = (int) questions.findByRoundIdOrderBySeq(roundId).stream().filter(QuizQuestion::isAnswered).count();
        boolean finished = answered >= round.getTotal();
        if (finished) {
            round.finish();
        }
        QuizAnswerResult.Reveal reveal;
        if (question.getQuoteId() == null) { // a cast or poster question carries its own answer
            reveal = new QuizAnswerResult.Reveal(question.getTitle(), question.getReleaseYear(), null, question.getTmdbId(), question.getMediaType(), round.getCategory());
        } else {
            Quote quote = quotes.findById(question.getQuoteId()).orElseThrow(() -> new NotFoundException("The quote behind that question no longer exists"));
            reveal = new QuizAnswerResult.Reveal(quote.getTitle(), quote.getReleaseYear(), quote.getSpeaker(), quote.getTmdbId(), quote.getMediaType(), quote.getCategory().name());
        }
        return new QuizAnswerResult(correct, question.getCorrectIndex(), points, speedBonus, round.getScore(), round.getStreak(), round.getBestStreak(), round.getCorrectCount(),
                answered, round.getTotal(), finished, reveal);
    }

    /**
     * The page reports that a question is on screen, which starts the clock for the poster game's speed bonus. Only the first report
     * counts, a question that is already answered is left alone, and the other kinds ignore it. Only the person's own round is
     * accepted.
     */
    @Transactional
    public void shown(String username, long roundId, int seq) {
        User user = userOf(username);
        rounds.findById(roundId).filter(r -> r.getUserId() == user.getId()).orElseThrow(() -> new NotFoundException("That quiz round was not found"));
        QuizQuestion question = questions.findByRoundIdAndSeq(roundId, seq).orElseThrow(() -> new NotFoundException("That question was not found"));
        if (POSTER.equals(question.getKind()) && !question.isAnswered()) {
            question.markShown(LocalDateTime.now());
        }
    }

    /** The speed bonus of a right answer: only in the poster game, only if the question was reported shown, measured on the server. */
    private int speedBonus(QuizQuestion question) {
        if (!POSTER.equals(question.getKind()) || question.getShownAt() == null) {
            return 0;
        }
        // in milliseconds: whole seconds would let an answer after 12.9 seconds count as "within 12"
        long millis = Duration.between(question.getShownAt(), LocalDateTime.now()).toMillis();
        return millis <= FAST_SECONDS * 1000L ? FAST_BONUS : millis <= SLOW_SECONDS * 1000L ? SLOW_BONUS : 0;
    }

    /** A title with the clue that will be shown for it (three actors joined by the separator, or the poster path). */
    private record TitleClue(Quote title, String clue) {
    }

    /**
     * The cast game and the poster game: ten different films and shows chosen at random from the titles of the quotes table, each
     * checked against TMDB for a usable clue (three named actors, or a poster). Candidates are tried in small parallel batches until
     * there are ten; if TMDB cannot supply enough, the answer is a plain "try again" error rather than a short round.
     */
    private QuizRoundView startTitleGame(User user, String category) {
        List<Quote> pool = quotes.findAll();
        Map<String, Quote> oneEach = new LinkedHashMap<>();
        List<Quote> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        for (Quote q : shuffled) {
            oneEach.putIfAbsent(q.getMediaType() + ":" + q.getTmdbId(), q);
        }
        List<Quote> candidates = new ArrayList<>(oneEach.values());

        List<TitleClue> chosen = new ArrayList<>();
        int next = 0;
        while (chosen.size() < QUESTIONS && next < candidates.size() && next < MAX_TITLE_ATTEMPTS) {
            int size = Math.min(QUESTIONS - chosen.size() + 2, candidates.size() - next);
            List<CompletableFuture<TitleClue>> batch = candidates.subList(next, next + size).stream()
                    .map(q -> CompletableFuture.supplyAsync(() -> clueFor(q, category), executor))
                    .toList();
            next += size;
            for (CompletableFuture<TitleClue> future : batch) {
                TitleClue clue = future.join();
                if (clue != null && chosen.size() < QUESTIONS) {
                    chosen.add(clue);
                }
            }
        }
        if (chosen.size() < QUESTIONS) {
            throw new UpstreamException(HttpStatus.BAD_GATEWAY, "Could not load enough films and shows from TMDB right now. Please try again in a moment");
        }

        QuizRound round = rounds.save(new QuizRound(user.getId(), category, QUESTIONS));
        List<QuizQuestionView> views = new ArrayList<>();
        for (int i = 0; i < chosen.size(); i++) {
            TitleClue item = chosen.get(i);
            Quote title = item.title();
            List<String> options = new ArrayList<>(wrongOptions(pool, title, true));
            int correctIndex = random.nextInt(OPTIONS);
            options.add(correctIndex, title.getTitle());
            questions.save(new QuizQuestion(round.getId(), i + 1, category, title.getTmdbId(), title.getMediaType(), title.getTitle(), title.getReleaseYear(), item.clue(), options, correctIndex));
            views.add(CAST.equals(category)
                    ? new QuizQuestionView(i + 1, CAST, "Which film or show stars these actors?", null, List.of(item.clue().split("\\|")), null, options)
                    : new QuizQuestionView(i + 1, POSTER, "Name this film or show from its poster", null, null, item.clue(), options));
        }
        return new QuizRoundView(round.getId(), category, QUESTIONS, views);
    }

    /** The clue for one title, or null when TMDB cannot supply one (no three named actors, no poster, or the lookup failed). */
    private TitleClue clueFor(Quote title, String category) {
        try {
            TitleDetail detail = catalog.detail(title.getMediaType(), title.getTmdbId());
            if (detail == null) {
                return null;
            }
            if (CAST.equals(category)) {
                List<String> names = detail.cast().stream().map(CastMember::name).filter(n -> n != null && !n.isBlank() && !n.contains("|")).limit(ACTORS_SHOWN).toList();
                return names.size() == ACTORS_SHOWN ? new TitleClue(title, String.join("|", names)) : null;
            }
            return detail.posterPath() == null ? null : new TitleClue(title, detail.posterPath());
        } catch (RuntimeException ex) {
            return null; // this title is unavailable right now; another is tried
        }
    }

    /** The ten best scores in a category (each person once, by their best finished round) and the asking person's own place. */
    @Transactional(readOnly = true)
    public QuizLeaderboard leaderboard(String username, String categoryName) {
        String category = validCategory(categoryName);
        List<Object[]> best = rounds.bestScores(category);
        Map<Long, User> byId = users.findAllById(best.stream().map(row -> (Long) row[0]).toList()).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        record Score(User user, int score) {
        }
        List<Score> scores = best.stream()
                .filter(row -> byId.containsKey((Long) row[0]))
                .map(row -> new Score(byId.get((Long) row[0]), ((Number) row[1]).intValue()))
                .sorted(Comparator.comparingInt(Score::score).reversed().thenComparing(s -> s.user().getUsername()))
                .toList();

        List<QuizLeaderboard.Row> rows = new ArrayList<>();
        QuizLeaderboard.Row you = null;
        for (Score s : scores) {
            int rank = 1 + (int) scores.stream().filter(other -> other.score() > s.score()).count(); // ties share a place
            boolean mine = s.user().getUsername().equals(username);
            QuizLeaderboard.Row row = new QuizLeaderboard.Row(rank, s.user().getUsername(), s.user().getDisplayName(), s.score(), mine);
            if (mine) {
                you = row;
            }
            if (rows.size() < BOARD_SIZE) {
                rows.add(row);
            }
        }
        return new QuizLeaderboard(category, rows, you);
    }

    private String validCategory(String name) {
        if (name == null || !CATEGORIES.contains(name)) {
            throw new BadRequestException("category must be ALL, HOLLYWOOD, TV, BOLLYWOOD, ANIME, CAST or POSTER");
        }
        return name;
    }

    private User userOf(String username) {
        return users.findByUsername(username).orElseThrow(() -> new NotFoundException("That account no longer exists"));
    }

    /** Three different wrong answers from the same category, from the same kind of title first so a show is not hidden among films. */
    private List<String> wrongOptions(List<Quote> pool, Quote quote, boolean titles) {
        String right = titles ? quote.getTitle() : quote.getSpeaker();
        List<Quote> shuffled = new ArrayList<>(pool);
        Collections.shuffle(shuffled, random);
        Set<String> wrong = new LinkedHashSet<>();
        for (boolean sameKindOnly : new boolean[] { true, false }) {
            for (Quote other : shuffled) {
                if (wrong.size() == OPTIONS - 1) {
                    break;
                }
                if (sameKindOnly && !other.getMediaType().equals(quote.getMediaType())) {
                    continue;
                }
                String text = titles ? other.getTitle() : other.getSpeaker();
                if (!text.equalsIgnoreCase(right)) {
                    wrong.add(text);
                }
            }
        }
        if (wrong.size() < OPTIONS - 1) {
            throw new ConflictException("There are not enough different quotes in this category to play yet");
        }
        return new ArrayList<>(wrong);
    }
}
