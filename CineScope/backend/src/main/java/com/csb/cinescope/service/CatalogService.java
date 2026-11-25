package com.csb.cinescope.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.Genre;
import com.csb.cinescope.dto.HomeResponse;
import com.csb.cinescope.dto.HomeRow;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.PersonDetail;
import com.csb.cinescope.dto.SearchItem;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.TitleSummary;
import com.csb.cinescope.dto.WatchProviders;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.AppException;
import com.csb.cinescope.mapper.TitleMapper;
import com.csb.cinescope.tmdb.TmdbGateway;
import com.csb.cinescope.tmdb.TmdbQuery;

import tools.jackson.databind.JsonNode;

/**
 * The catalogue use cases: home page, browse lists, filtered discovery, search, genres and title details.
 *
 * <p>Connections: called by {@code controller/CatalogController}; fetches JSON through the {@code tmdb/TmdbGateway}
 * interface (cached, see {@code tmdb/TmdbHttpGateway}) and converts it with {@code mapper/TitleMapper}. Defaults for
 * language and country (India) come from {@code config/CineScopeProperties}. This class owns the rules about what may
 * be asked (valid media types, categories, sort options, page limits) and rejects anything else with a 400 before
 * TMDB is ever called.
 */
@Service
public class CatalogService {

    public static final int MAX_PAGE = 500;
    /** How many titles rotate in the home banner. */
    private static final int MAX_HEROES = 6;
    private static final Set<String> MEDIA_TYPES = Set.of("movie", "tv");
    private static final Set<String> LIST_CATEGORIES = Set.of("trending", "trending_today", "popular", "top_rated", "now_playing", "upcoming", "on_the_air");
    private static final Map<String, Set<String>> CATEGORY_TYPES = Map.of(
            "now_playing", Set.of("movie"),
            "upcoming", Set.of("movie"),
            "on_the_air", Set.of("tv"));
    private static final Set<String> SORTS = Set.of("popularity", "rating", "newest");

    /** The rows of the home page, in display order. */
    private record RowSpec(String key, String title, String category, String mediaType) {
    }

    private static final List<RowSpec> HOME_ROWS = List.of(
            new RowSpec("trending-today", "Trending today", "trending_today", "all"),
            new RowSpec("trending", "Trending this week", "trending", "all"),
            new RowSpec("popular-movies", "Popular movies", "popular", "movie"),
            new RowSpec("now-playing", "In cinemas now", "now_playing", "movie"),
            new RowSpec("top-rated-movies", "Top rated movies", "top_rated", "movie"),
            new RowSpec("upcoming", "Coming soon", "upcoming", "movie"),
            new RowSpec("popular-tv", "Popular TV shows", "popular", "tv"),
            new RowSpec("top-rated-tv", "Top rated TV shows", "top_rated", "tv"));

    private final TmdbGateway tmdb;
    private final TitleMapper mapper;
    private final CineScopeProperties properties;
    private final ExecutorService executor;

    public CatalogService(TmdbGateway tmdb, TitleMapper mapper, CineScopeProperties properties, ExecutorService catalogExecutor) {
        this.tmdb = tmdb;
        this.mapper = mapper;
        this.properties = properties;
        this.executor = catalogExecutor;
    }

    // ---- home ----

    /**
     * The home page: a hero title plus the rows above. The rows are fetched in parallel; a row that fails is left out
     * rather than breaking the page, and only when every row fails does the first error reach the caller.
     */
    public HomeResponse home() {
        List<CompletableFuture<HomeRow>> futures = HOME_ROWS.stream()
                .map(spec -> CompletableFuture.supplyAsync(() -> row(spec), executor))
                .toList();

        List<HomeRow> rows = new ArrayList<>();
        RuntimeException firstFailure = null;
        for (CompletableFuture<HomeRow> future : futures) {
            try {
                rows.add(future.join());
            } catch (CompletionException ex) {
                if (firstFailure == null) {
                    firstFailure = ex.getCause() instanceof AppException c ? c : new IllegalStateException(ex.getCause());
                }
            }
        }
        if (rows.isEmpty() && firstFailure != null) {
            throw firstFailure;
        }
        List<TitleSummary> heroes = pickHeroes(rows);
        return new HomeResponse(heroes.isEmpty() ? null : heroes.get(0), heroes, rows);
    }

    private HomeRow row(RowSpec spec) {
        PageResult<TitleSummary> page = list(spec.category(), spec.mediaType(), 1);
        return new HomeRow(spec.key(), spec.title(), spec.category(), spec.mediaType(), page.items());
    }

    /**
     * The titles of the rotating banner: up to MAX_HEROES of today's trending titles that have both a backdrop picture and a
     * description, topped up from this week's trending when today's list is short. If nothing qualifies, any title with a
     * backdrop is used so the banner is not left empty.
     */
    private List<TitleSummary> pickHeroes(List<HomeRow> rows) {
        List<TitleSummary> heroes = new ArrayList<>();
        for (String key : List.of("trending-today", "trending")) {
            rows.stream().filter(row -> row.key().equals(key)).flatMap(row -> row.items().stream())
                    .filter(t -> t.backdropPath() != null && t.overview() != null)
                    .filter(t -> heroes.stream().noneMatch(h -> h.id() == t.id() && h.type().equals(t.type())))
                    .limit(Math.max(0, MAX_HEROES - heroes.size()))
                    .forEach(heroes::add);
        }
        if (heroes.isEmpty()) {
            rows.stream().flatMap(r -> r.items().stream()).filter(t -> t.backdropPath() != null).findFirst().ifPresent(heroes::add);
        }
        return heroes;
    }

    // ---- lists ----

    /** A named TMDB list: trending, popular, top_rated, now_playing, upcoming (movies) or on_the_air (TV). */
    public PageResult<TitleSummary> list(String category, String type, int page) {
        requirePage(page);
        if (!LIST_CATEGORIES.contains(category)) {
            throw new BadRequestException("Unknown category '" + category + "'. Use one of " + new java.util.TreeSet<>(LIST_CATEGORIES));
        }
        boolean trending = category.startsWith("trending");
        if (!(trending && type.equals("all")) && !MEDIA_TYPES.contains(type)) {
            throw new BadRequestException("Unknown type '" + type + "'. Use movie or tv" + (trending ? " (or all for trending)" : ""));
        }
        if (CATEGORY_TYPES.containsKey(category) && !CATEGORY_TYPES.get(category).contains(type)) {
            throw new BadRequestException("Category '" + category + "' is only available for " + CATEGORY_TYPES.get(category));
        }

        String path = trending ? "/trending/" + type + (category.equals("trending_today") ? "/day" : "/week") : "/" + type + "/" + category;
        TmdbQuery query = baseQuery().with("page", page);
        if (type.equals("movie") && !trending) {
            query.with("region", properties.defaultRegion());
        }
        JsonNode json = tmdb.lists(path, query.build());
        return mapper.summaryPage(json, trending && type.equals("all") ? "movie" : type);
    }

    /** Filtered browsing: optional genre, release year and minimum rating, sorted by popularity, rating or newest. */
    public PageResult<TitleSummary> discover(String type, Integer genre, Integer year, Double minRating, String sort, int page) {
        requireMediaType(type);
        requirePage(page);
        if (!SORTS.contains(sort)) {
            throw new BadRequestException("Unknown sort '" + sort + "'. Use popularity, rating or newest");
        }
        if (year != null && (year < 1888 || year > LocalDate.now().getYear() + 5)) {
            throw new BadRequestException("Year is out of range");
        }
        if (minRating != null && (minRating < 0 || minRating > 10)) {
            throw new BadRequestException("minRating must be between 0 and 10");
        }

        boolean movie = type.equals("movie");
        String dateField = movie ? "primary_release_date" : "first_air_date";
        TmdbQuery query = baseQuery()
                .with("page", page)
                .with("include_adult", false)
                .with("with_genres", genre)
                .with(movie ? "primary_release_year" : "first_air_date_year", year);
        if (minRating != null && minRating > 0) {
            query.with("vote_average.gte", minRating).with("vote_count.gte", 50);
        }
        switch (sort) {
            case "rating" -> query.with("sort_by", "vote_average.desc").with("vote_count.gte", 300);
            case "newest" -> query.with("sort_by", dateField + ".desc").with(dateField + ".lte", LocalDate.now()).with("vote_count.gte", 5);
            default -> query.with("sort_by", "popularity.desc");
        }
        return mapper.summaryPage(tmdb.lists("/discover/" + type, query.build()), type);
    }

    // ---- search ----

    /** Searches movies, TV shows and people at once. */
    public PageResult<SearchItem> search(String text, int page) {
        requirePage(page);
        String query = text == null ? "" : text.trim();
        if (query.isEmpty() || query.length() > 100) {
            throw new BadRequestException("Search text must be 1 to 100 characters");
        }
        SortedMap<String, String> params = baseQuery().with("query", query).with("include_adult", false).with("page", page).build();
        return mapper.searchPage(tmdb.lists("/search/multi", params));
    }

    // ---- reference data and details ----

    public List<Genre> genres(String type) {
        requireMediaType(type);
        return mapper.genres(tmdb.reference("/genre/" + type + "/list", baseQuery().build()));
    }

    public TitleDetail detail(String type, long id) {
        requireMediaType(type);
        if (id <= 0) {
            throw new BadRequestException("id must be positive");
        }
        String appended = type.equals("movie")
                ? "credits,videos,recommendations,similar,release_dates"
                : "credits,videos,recommendations,similar,content_ratings";
        SortedMap<String, String> params = baseQuery().with("append_to_response", appended).build();
        return mapper.detail(tmdb.detail("/" + type + "/" + id, params), type, properties.defaultRegion());
    }

    /** A person with their filmography (TMDB's person answer plus combined credits, in one request). */
    public PersonDetail person(long id) {
        if (id <= 0) {
            throw new BadRequestException("id must be positive");
        }
        SortedMap<String, String> params = baseQuery().with("append_to_response", "combined_credits").build();
        return mapper.person(tmdb.detail("/person/" + id, params));
    }

    /**
     * Where a title can be watched in a country (default: the configured one, India). {@code region} is an ISO 3166-1
     * code such as IN or US, in any letter case. Uses TMDB's {@code /watch/providers}, which answers for all countries
     * at once, so the answer is cached per title and not per country.
     */
    public WatchProviders providers(String type, long id, String region) {
        requireMediaType(type);
        if (id <= 0) {
            throw new BadRequestException("id must be positive");
        }
        String country = region == null || region.isBlank() ? properties.defaultRegion() : region.trim().toUpperCase(java.util.Locale.ROOT);
        if (!country.matches("[A-Z]{2}")) {
            throw new BadRequestException("region must be a two-letter country code such as IN or US");
        }
        return mapper.providers(tmdb.detail("/" + type + "/" + id + "/watch/providers", TmdbQuery.create().build()), country);
    }

    // ---- helpers ----

    private TmdbQuery baseQuery() {
        return TmdbQuery.create().with("language", properties.language());
    }

    private void requireMediaType(String type) {
        if (!MEDIA_TYPES.contains(type)) {
            throw new BadRequestException("Unknown type '" + type + "'. Use movie or tv");
        }
    }

    private void requirePage(int page) {
        if (page < 1 || page > MAX_PAGE) {
            throw new BadRequestException("page must be between 1 and " + MAX_PAGE);
        }
    }
}
