package com.csb.cinescope.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.Recommendation;
import com.csb.cinescope.dto.Suggestion;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.TitleSummary;
import com.csb.cinescope.dto.WatchProviders;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.exception.AppException;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.mapper.TitleMapper;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.tmdb.TmdbGateway;
import com.csb.cinescope.tmdb.TmdbQuery;

/**
 * "What should I watch tonight?": turns a few wishes (movie or show, mood, time available, language, only what streams
 * in the default country) into one pick and a handful of alternatives.
 *
 * <p>How it works. The wishes become one TMDB {@code /discover} query (mood = a set of genres, time = maximum runtime,
 * streaming = TMDB's availability filter for the default region, India) for well-rated, well-known titles, two pages deep
 * so there is a real choice. For a signed-in user, titles they marked WATCHED or WATCHING are dropped and titles on their
 * "want to watch" list are boosted (read through {@code repository/WatchlistItemRepository}). Each remaining candidate
 * scores its TMDB rating plus a seeded random bonus (so tonight's pick is not always the single top-rated film) plus the
 * watchlist boost; the best score is the pick. The same wishes and seed always give the same answer.
 *
 * <p>Why runtimes are checked here: TMDB's own maximum-runtime filter is unreliable (a 169-minute Hindi film passes
 * "under 120"), so when a time limit was asked for, the best-ranked candidates are looked up (in parallel; cached by the
 * gateway) and those whose real runtime is longer are dropped. A candidate whose runtime TMDB does not know, or cannot
 * be looked up, is kept rather than wrongly rejected.
 *
 * <p>Connections: called by {@code controller/TonightController}; fetches through {@code tmdb/TmdbGateway} (cached)
 * and maps with {@code mapper/TitleMapper}; the pick's details and "where to watch" come from {@code CatalogService},
 * best effort (failure leaves them null). Anything invalid is a 400 before TMDB is called.
 */
@Service
public class RecommendationService {

    private static final int ALTERNATIVES = 5;
    private static final double RANDOM_SPREAD = 2.0;
    private static final double WATCHLIST_BOOST = 3.0;
    /** How many of the best-ranked candidates get their real runtime checked when a time limit was asked for. */
    private static final int RUNTIME_CHECKS = 16;
    /** How many of the best-ranked candidates get their age rating checked when only family-safe titles were asked for. */
    private static final int FAMILY_CHECKS = 30;

    /** Mood name to TMDB genre ids ("|" means any of them). Movies and TV have different genre lists. */
    private static final Map<String, Map<String, String>> MOODS = new LinkedHashMap<>();
    private static final Map<String, String> MOOD_LABELS = new LinkedHashMap<>();

    static {
        mood("laugh", "Make me laugh", "35", "35");
        mood("thrill", "Edge of my seat", "53|80", "80|9648");
        mood("mind", "Mind-bending", "878|9648", "10765|9648");
        mood("romance", "Romantic", "10749", "18");
        mood("adventure", "Big adventure", "12|28", "10759");
        mood("family", "Family night", "10751|16", "10751|16");
        mood("scary", "Spooky", "27", "9648");
    }

    private static void mood(String key, String label, String movieGenres, String tvGenres) {
        MOODS.put(key, Map.of("movie", movieGenres, "tv", tvGenres));
        MOOD_LABELS.put(key, label);
    }

    private final TmdbGateway tmdb;
    private final TitleMapper mapper;
    private final CineScopeProperties properties;
    private final CatalogService catalog;
    private final UserRepository users;
    private final WatchlistItemRepository watchlist;
    private final ExecutorService executor;

    public RecommendationService(TmdbGateway tmdb, TitleMapper mapper, CineScopeProperties properties, CatalogService catalog,
                                 UserRepository users, WatchlistItemRepository watchlist, ExecutorService catalogExecutor) {
        this.tmdb = tmdb;
        this.mapper = mapper;
        this.properties = properties;
        this.catalog = catalog;
        this.users = users;
        this.watchlist = watchlist;
        this.executor = catalogExecutor;
    }

    /** The mood names the endpoint accepts (the frontend offers the same list). */
    public static Set<String> moods() {
        return MOODS.keySet();
    }

    /**
     * @param username    the signed-in user, or null for a visitor (no personalisation)
     * @param type        movie or tv
     * @param mood        one of {@link #moods()}, or null for any
     * @param maxMinutes  longest acceptable runtime (30 to 300; per episode for TV), or null
     * @param language    two-letter original language such as hi or en, or null for any
     * @param streamingOnly only titles available to stream (subscription, free or with adverts) in the default country
     * @param seed        makes the draw repeatable; null picks a fresh one
     */
    public Recommendation tonight(String username, String type, String mood, Integer maxMinutes, String language,
                                  boolean streamingOnly, Long seed) {
        return tonight(username, type, mood, maxMinutes, language, streamingOnly, false, seed);
    }

    /**
     * Same as above, with {@code familySafe}: only titles whose age rating means suitable for everyone ({@code mapper/AgeRating}); a
     * title with no rating, or whose rating cannot be looked up, is left out.
     */
    public Recommendation tonight(String username, String type, String mood, Integer maxMinutes, String language,
                                  boolean streamingOnly, boolean familySafe, Long seed) {
        if (!type.equals("movie") && !type.equals("tv")) {
            throw new BadRequestException("Unknown type '" + type + "'. Use movie or tv");
        }
        if (mood != null && !MOODS.containsKey(mood)) {
            throw new BadRequestException("Unknown mood '" + mood + "'. Use one of " + MOODS.keySet());
        }
        if (maxMinutes != null && (maxMinutes < 30 || maxMinutes > 300)) {
            throw new BadRequestException("maxMinutes must be between 30 and 300");
        }
        String lang = language == null || language.isBlank() ? null : language.trim().toLowerCase(Locale.ROOT);
        if (lang != null && !lang.matches("[a-z]{2}")) {
            throw new BadRequestException("language must be a two-letter code such as en or hi");
        }
        long drawSeed = seed != null ? seed : System.nanoTime();

        Map<Long, WatchStatus> mine = statuses(username, type);
        List<TitleSummary> candidates = candidates(type, mood, maxMinutes, lang, streamingOnly, mine);

        Random random = new Random(drawSeed);
        Map<Long, Double> score = new HashMap<>();
        for (TitleSummary t : candidates) {
            score.put(t.id(), t.rating() + random.nextDouble() * RANDOM_SPREAD + (mine.get(t.id()) == WatchStatus.WANT ? WATCHLIST_BOOST : 0));
        }
        List<Suggestion> ranked = candidates.stream()
                .sorted(Comparator.comparingDouble((TitleSummary t) -> score.get(t.id())).reversed())
                .map(t -> suggestion(t, mine.get(t.id()) == WatchStatus.WANT))
                .toList();
        ranked = withinRuntime(type, ranked, maxMinutes);
        ranked = familySafe ? familyOnly(type, ranked) : ranked;

        if (ranked.isEmpty()) {
            return new Recommendation(null, null, null, List.of(), List.of(), 0, drawSeed);
        }
        Suggestion pick = ranked.get(0);
        List<Suggestion> alternatives = ranked.subList(1, Math.min(ranked.size(), 1 + ALTERNATIVES));
        return new Recommendation(pick, detailsOf(type, pick.id()), providersOf(type, pick.id()), alternatives,
                reasons(pick, mood, maxMinutes, lang, streamingOnly, familySafe), ranked.size(), drawSeed);
    }

    // ---- steps ----

    /** The user's status for each title of this type they have listed (empty for a visitor). */
    private Map<Long, WatchStatus> statuses(String username, String type) {
        Map<Long, WatchStatus> result = new HashMap<>();
        if (username == null) {
            return result;
        }
        User user = users.findByUsername(username).orElse(null);
        if (user != null) {
            for (WatchlistItemRepository.Tagged item : watchlist.findTagged(user, type)) {
                result.put(item.getTmdbId(), item.getStatus());
            }
        }
        return result;
    }

    /** Two pages of well-rated popular titles matching the wishes, without posterless ones and what the user has seen. */
    private List<TitleSummary> candidates(String type, String mood, Integer maxMinutes, String language, boolean streamingOnly,
                                          Map<Long, WatchStatus> mine) {
        Map<Long, TitleSummary> byId = new LinkedHashMap<>();
        for (int page = 1; page <= 2; page++) {
            try {
                for (TitleSummary t : mapper.summaryPage(tmdb.lists("/discover/" + type, discoverQuery(type, mood, maxMinutes, language, streamingOnly, page)), type).items()) {
                    WatchStatus status = mine.get(t.id());
                    boolean seen = status == WatchStatus.WATCHED || status == WatchStatus.WATCHING;
                    if (t.posterPath() != null && !seen) {
                        byId.putIfAbsent(t.id(), t);
                    }
                }
            } catch (AppException ex) {
                if (page == 1) {
                    throw ex; // no candidates at all: the caller should hear about it
                }
                // a failing second page only means fewer candidates
            }
        }
        return new ArrayList<>(byId.values());
    }

    private SortedMap<String, String> discoverQuery(String type, String mood, Integer maxMinutes, String language, boolean streamingOnly, int page) {
        TmdbQuery query = TmdbQuery.create()
                .with("language", properties.language())
                .with("page", page)
                .with("include_adult", false)
                .with("sort_by", "popularity.desc")
                .with("vote_average.gte", 6.5)
                .with("vote_count.gte", type.equals("movie") ? 300 : 150)
                .with("with_genres", mood == null ? null : MOODS.get(mood).get(type))
                .with("with_runtime.lte", maxMinutes)
                .with("with_original_language", language);
        if (streamingOnly) {
            query.with("watch_region", properties.defaultRegion()).with("with_watch_monetization_types", "flatrate|free|ads");
        }
        return query.build();
    }

    /**
     * Keeps only candidates that really fit the time limit (see the class comment). Checks the best RUNTIME_CHECKS in
     * parallel and drops the rest of the list: it is ranked below them anyway, and looking up hundreds of titles per
     * request would be slow. Without a time limit nothing is looked up.
     */
    private List<Suggestion> withinRuntime(String type, List<Suggestion> ranked, Integer maxMinutes) {
        if (maxMinutes == null) {
            return ranked;
        }
        List<Suggestion> head = ranked.subList(0, Math.min(ranked.size(), RUNTIME_CHECKS));
        List<CompletableFuture<Boolean>> fits = head.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> fits(type, s.id(), maxMinutes), executor))
                .toList();
        List<Suggestion> kept = new ArrayList<>();
        for (int i = 0; i < head.size(); i++) {
            if (fits.get(i).join()) {
                kept.add(head.get(i));
            }
        }
        return kept;
    }

    /**
     * Keeps only candidates whose age rating means suitable for everyone. Like the runtime check it looks at the best
     * FAMILY_CHECKS only, in parallel (the details are cached for an hour). Unlike the runtime check, a title that cannot be
     * looked up is dropped: for a family, "unknown" is not good enough.
     */
    private List<Suggestion> familyOnly(String type, List<Suggestion> ranked) {
        List<Suggestion> head = ranked.subList(0, Math.min(ranked.size(), FAMILY_CHECKS));
        List<CompletableFuture<Boolean>> safe = head.stream()
                .map(s -> CompletableFuture.supplyAsync(() -> isFamilyFriendly(type, s.id()), executor))
                .toList();
        List<Suggestion> kept = new ArrayList<>();
        for (int i = 0; i < head.size(); i++) {
            if (safe.get(i).join()) {
                kept.add(head.get(i));
            }
        }
        return kept;
    }

    private boolean isFamilyFriendly(String type, long id) {
        try {
            TitleDetail detail = catalog.detail(type, id);
            return detail != null && detail.familyFriendly();
        } catch (AppException ex) {
            return false;
        }
    }

    private boolean fits(String type, long id, int maxMinutes) {
        try {
            TitleDetail detail = catalog.detail(type, id);
            return detail == null || detail.runtime() == null || detail.runtime() <= maxMinutes;
        } catch (AppException ex) {
            return true; // cannot tell: do not reject on a lookup failure
        }
    }

    private static Suggestion suggestion(TitleSummary t, boolean onWatchlist) {
        return new Suggestion(t.id(), t.type(), t.title(), t.overview(), t.posterPath(), t.backdropPath(), t.year(), t.rating(), onWatchlist);
    }

    private TitleDetail detailsOf(String type, long id) {
        try {
            return catalog.detail(type, id);
        } catch (AppException ex) {
            return null;
        }
    }

    private WatchProviders providersOf(String type, long id) {
        try {
            return catalog.providers(type, id, null);
        } catch (AppException ex) {
            return null;
        }
    }

    private List<String> reasons(Suggestion pick, String mood, Integer maxMinutes, String language, boolean streamingOnly, boolean familySafe) {
        List<String> reasons = new ArrayList<>();
        if (pick.onWatchlist()) {
            reasons.add("On your watchlist");
        }
        if (mood != null) {
            reasons.add(MOOD_LABELS.get(mood));
        }
        if (maxMinutes != null) {
            reasons.add("Up to " + maxMinutes + " min" + (pick.type().equals("tv") ? " per episode" : ""));
        }
        if (language != null) {
            reasons.add("Language: " + language.toUpperCase(Locale.ROOT));
        }
        if (streamingOnly) {
            reasons.add("Streaming in " + properties.defaultRegion());
        }
        if (familySafe) {
            reasons.add("Suitable for all ages");
        }
        if (pick.rating() > 0) {
            reasons.add(String.format(Locale.ROOT, "Rated %.1f", pick.rating()));
        }
        return reasons;
    }
}
