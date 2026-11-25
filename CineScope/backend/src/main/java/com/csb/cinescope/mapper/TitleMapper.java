package com.csb.cinescope.mapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.csb.cinescope.dto.CastMember;
import com.csb.cinescope.dto.CrewMember;
import com.csb.cinescope.dto.Genre;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.PersonCredit;
import com.csb.cinescope.dto.PersonDetail;
import com.csb.cinescope.dto.SearchItem;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.TitleSummary;
import com.csb.cinescope.dto.Video;
import com.csb.cinescope.dto.WatchProvider;
import com.csb.cinescope.dto.WatchProviders;

import tools.jackson.databind.JsonNode;

/**
 * Turns TMDB's JSON ({@link JsonNode}) into CineScope's own records ({@code dto/}). All the quirks of TMDB live here
 * and nowhere else: movies use {@code title} / {@code release_date} while shows use {@code name} /
 * {@code first_air_date}; almost any field can be missing, null or empty; lists arrive wrapped in {@code results}.
 *
 * <p>Used by {@code service/CatalogService}, which fetches the JSON through {@code tmdb/TmdbGateway}. Missing data never
 * throws: it becomes null, 0 or an empty list, so one incomplete title cannot break a whole page.
 */
@Component
public class TitleMapper {

    private static final int MAX_TMDB_PAGES = 500;
    private static final int MAX_CAST = 20;
    private static final int MAX_SIMILAR = 12;
    private static final int MAX_VIDEOS = 6;
    private static final Set<String> VIDEO_TYPES = Set.of("Trailer", "Teaser");
    private static final int MAX_KNOWN_FOR = 12;
    private static final int MAX_CREDITS = 200;
    /** A cast credit counts as a real role in "known for" only when billed this high (TMDB's order starts at 0). */
    private static final int MAX_KNOWN_FOR_BILLING = 15;
    /** ...and a TV credit only when the person was in at least this many episodes (guest cameos do not count). */
    private static final int MIN_KNOWN_FOR_EPISODES = 3;
    private static final Set<String> KNOWN_FOR_JOBS = Set.of("Director", "Writer", "Screenplay", "Creator", "Producer");
    /** Genres of programmes a person merely appears on (talk shows 10767, news 10763): hidden from filmographies. */
    private static final Set<Integer> APPEARANCE_GENRES = Set.of(10767, 10763);

    // ---- lists ----

    /** A page of movies / shows. {@code defaultType} is used when an item has no {@code media_type} of its own. */
    public PageResult<TitleSummary> summaryPage(JsonNode root, String defaultType) {
        return new PageResult<>(summaryItems(root.path("results"), defaultType), root.path("page").asInt(1), pages(root),
                root.path("total_results").asLong(0));
    }

    /** The movies / shows in a JSON array of TMDB items; entries that are not movies or shows (people) are skipped. */
    public List<TitleSummary> summaryItems(JsonNode array, String defaultType) {
        List<TitleSummary> items = new ArrayList<>();
        for (JsonNode node : array) {
            String type = typeOf(node, defaultType);
            if (isTitleType(type) && node.path("id").isNumber()) {
                items.add(summary(node, type));
            }
        }
        return items;
    }

    /** One movie / show. */
    public TitleSummary summary(JsonNode node, String type) {
        String date = firstText(node, "release_date", "first_air_date");
        return new TitleSummary(
                node.path("id").asLong(),
                type,
                firstText(node, "title", "name"),
                text(node, "overview"),
                text(node, "poster_path"),
                text(node, "backdrop_path"),
                yearOf(date),
                node.path("vote_average").asDouble(0));
    }

    /** A page of multi-search hits (movies, shows and people; anything else is dropped). */
    public PageResult<SearchItem> searchPage(JsonNode root) {
        List<SearchItem> items = new ArrayList<>();
        for (JsonNode node : root.path("results")) {
            String type = text(node, "media_type");
            if (!node.path("id").isNumber() || type == null) {
                continue;
            }
            if (isTitleType(type)) {
                TitleSummary s = summary(node, type);
                items.add(new SearchItem(s.id(), type, s.title(), s.posterPath(), s.year(), s.rating(), s.overview(), null));
            } else if (type.equals("person")) {
                items.add(new SearchItem(node.path("id").asLong(), "person", text(node, "name"), text(node, "profile_path"),
                        null, 0, null, knownFor(node)));
            }
        }
        return new PageResult<>(items, root.path("page").asInt(1), pages(root), root.path("total_results").asLong(0));
    }

    public List<Genre> genres(JsonNode root) {
        return genreList(root.path("genres"));
    }

    private List<Genre> genreList(JsonNode array) {
        List<Genre> genres = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.path("id").isNumber() && text(node, "name") != null) {
                genres.add(new Genre(node.path("id").asInt(), text(node, "name")));
            }
        }
        return genres;
    }

    // ---- detail ----

    /**
     * The detail page of one title. {@code root} is TMDB's answer for {@code /movie/{id}} or {@code /tv/{id}} with
     * credits, videos, recommendations, similar and release dates / content ratings appended.
     */
    public TitleDetail detail(JsonNode root, String type, String region) {
        boolean movie = type.equals("movie");
        String date = firstText(root, "release_date", "first_air_date");
        Integer runtime = movie ? positiveOrNull(root.path("runtime").asInt(0)) : firstEpisodeRuntime(root);
        String certification = certification(root, movie, region);

        return new TitleDetail(
                root.path("id").asLong(),
                type,
                firstText(root, "title", "name"),
                text(root, "tagline"),
                text(root, "overview"),
                text(root, "poster_path"),
                text(root, "backdrop_path"),
                yearOf(date),
                date,
                runtime,
                root.path("vote_average").asDouble(0),
                root.path("vote_count").asInt(0),
                text(root, "status"),
                text(root, "original_language"),
                certification,
                genreList(root.path("genres")),
                movie ? directors(root.path("credits").path("crew")) : creators(root.path("created_by")),
                cast(root.path("credits").path("cast")),
                videos(root.path("videos").path("results")),
                similar(root, type),
                movie ? positiveLongOrNull(root.path("budget").asLong(0)) : null,
                movie ? positiveLongOrNull(root.path("revenue").asLong(0)) : null,
                movie ? null : positiveOrNull(root.path("number_of_seasons").asInt(0)),
                movie ? null : positiveOrNull(root.path("number_of_episodes").asInt(0)),
                text(root, "homepage"),
                AgeRating.isFamilyFriendly(certification));
    }

    // ---- detail helpers ----

    private List<CrewMember> directors(JsonNode crew) {
        List<CrewMember> result = new ArrayList<>();
        for (JsonNode node : crew) {
            if ("Director".equals(text(node, "job")) && text(node, "name") != null) {
                result.add(new CrewMember(node.path("id").asLong(), text(node, "name"), "Director"));
            }
        }
        return result;
    }

    private List<CrewMember> creators(JsonNode createdBy) {
        List<CrewMember> result = new ArrayList<>();
        for (JsonNode node : createdBy) {
            if (text(node, "name") != null) {
                result.add(new CrewMember(node.path("id").asLong(), text(node, "name"), "Creator"));
            }
        }
        return result;
    }

    private List<CastMember> cast(JsonNode castNode) {
        List<CastMember> result = new ArrayList<>();
        for (JsonNode node : castNode) {
            if (result.size() >= MAX_CAST) {
                break;
            }
            if (text(node, "name") != null) {
                result.add(new CastMember(node.path("id").asLong(), text(node, "name"), text(node, "character"),
                        text(node, "profile_path")));
            }
        }
        return result;
    }

    /** YouTube trailers and teasers, official ones first and trailers before teasers. */
    private List<Video> videos(JsonNode results) {
        List<Video> all = new ArrayList<>();
        for (JsonNode node : results) {
            String type = text(node, "type");
            if ("YouTube".equals(text(node, "site")) && type != null && VIDEO_TYPES.contains(type) && text(node, "key") != null) {
                all.add(new Video(text(node, "key"), text(node, "name"), type, node.path("official").asBoolean(false)));
            }
        }
        all.sort(Comparator.comparing((Video v) -> !v.official()).thenComparing(v -> !v.type().equals("Trailer")));
        return all.size() > MAX_VIDEOS ? new ArrayList<>(all.subList(0, MAX_VIDEOS)) : all;
    }

    private List<TitleSummary> similar(JsonNode root, String type) {
        List<TitleSummary> items = summaryItems(root.path("recommendations").path("results"), type);
        if (items.isEmpty()) {
            items = summaryItems(root.path("similar").path("results"), type);
        }
        return items.size() > MAX_SIMILAR ? new ArrayList<>(items.subList(0, MAX_SIMILAR)) : items;
    }

    /** Age rating for {@code region}, else the US one, else null. */
    private String certification(JsonNode root, boolean movie, String region) {
        for (String country : List.of(region, "US")) {
            if (movie) {
                for (JsonNode entry : root.path("release_dates").path("results")) {
                    if (country.equals(text(entry, "iso_3166_1"))) {
                        for (JsonNode release : entry.path("release_dates")) {
                            String value = text(release, "certification");
                            if (value != null) {
                                return value;
                            }
                        }
                    }
                }
            } else {
                for (JsonNode entry : root.path("content_ratings").path("results")) {
                    if (country.equals(text(entry, "iso_3166_1")) && text(entry, "rating") != null) {
                        return text(entry, "rating");
                    }
                }
            }
        }
        return null;
    }

    private Integer firstEpisodeRuntime(JsonNode root) {
        JsonNode runtimes = root.path("episode_run_time");
        return runtimes.isArray() && runtimes.size() > 0 ? positiveOrNull(runtimes.path(0).asInt(0)) : null;
    }

    private String knownFor(JsonNode person) {
        List<String> names = new ArrayList<>();
        for (JsonNode node : person.path("known_for")) {
            String name = firstText(node, "title", "name");
            if (name != null && names.size() < 3) {
                names.add(name);
            }
        }
        return names.isEmpty() ? null : String.join(", ", names);
    }

    // ---- small JSON helpers ----

    private static String typeOf(JsonNode node, String defaultType) {
        String type = text(node, "media_type");
        return type != null ? type : defaultType;
    }

    private static boolean isTitleType(String type) {
        return "movie".equals(type) || "tv".equals(type);
    }

    private static int pages(JsonNode root) {
        return Math.min(root.path("total_pages").asInt(1), MAX_TMDB_PAGES);
    }

    // ---- people ----

    /**
     * A person from TMDB's {@code /person/{id}} answer with {@code combined_credits} appended. The filmography keeps only
     * movies and shows, each title once (a crew member with several jobs on one title gets one line, "Director, Writer"),
     * newest first with undated titles last, and skips talk shows and news. {@code knownFor} is the titles with the most
     * votes that have a poster, counting only real work: top-billed acting roles, TV shows with several episodes and the
     * main crew jobs (so a one-episode guest spot on a famous sitcom is not "known for"). Every list is capped so a very
     * long career cannot make a huge answer.
     */
    public PersonDetail person(JsonNode root) {
        JsonNode credits = root.path("combined_credits");
        Map<String, PersonCredit> acting = new LinkedHashMap<>();
        Map<String, PersonCredit> crew = new LinkedHashMap<>();
        Map<String, Integer> votes = new LinkedHashMap<>();
        java.util.Set<String> prominent = new java.util.HashSet<>();

        for (JsonNode node : credits.path("cast")) {
            PersonCredit credit = credit(node, text(node, "character"));
            if (credit != null) {
                acting.putIfAbsent(key(credit), credit);
                votes.merge(key(credit), node.path("vote_count").asInt(0), Math::max);
                if (node.path("order").asInt(0) <= MAX_KNOWN_FOR_BILLING
                        && ("movie".equals(credit.type()) || node.path("episode_count").asInt(MIN_KNOWN_FOR_EPISODES) >= MIN_KNOWN_FOR_EPISODES)) {
                    prominent.add(key(credit));
                }
            }
        }
        for (JsonNode node : credits.path("crew")) {
            PersonCredit credit = credit(node, text(node, "job"));
            if (credit != null) {
                crew.merge(key(credit), credit, (old, next) -> withRole(old, joinRoles(old.role(), next.role())));
                votes.merge(key(credit), node.path("vote_count").asInt(0), Math::max);
                if (KNOWN_FOR_JOBS.contains(text(node, "job"))) {
                    prominent.add(key(credit));
                }
            }
        }

        List<PersonCredit> knownFor = new ArrayList<>();
        Map<String, PersonCredit> everything = new LinkedHashMap<>(crew);
        everything.putAll(acting);
        boolean anyProminent = everything.keySet().stream().anyMatch(prominent::contains);
        everything.values().stream()
                .filter(c -> c.posterPath() != null && (!anyProminent || prominent.contains(key(c))))
                .sorted(Comparator.comparingInt((PersonCredit c) -> votes.getOrDefault(key(c), 0)).reversed())
                .limit(MAX_KNOWN_FOR)
                .forEach(knownFor::add);

        return new PersonDetail(
                root.path("id").asLong(),
                text(root, "name"),
                text(root, "biography"),
                text(root, "birthday"),
                text(root, "deathday"),
                text(root, "place_of_birth"),
                text(root, "known_for_department"),
                text(root, "profile_path"),
                text(root, "homepage"),
                knownFor,
                newestFirst(acting),
                newestFirst(crew));
    }

    /** One filmography line, or null for anything that is not a movie / show, is untitled, or is a talk / news appearance. */
    private PersonCredit credit(JsonNode node, String role) {
        String type = text(node, "media_type");
        String title = firstText(node, "title", "name");
        if (!isTitleType(type) || title == null) {
            return null;
        }
        for (JsonNode genre : node.path("genre_ids")) {
            if (APPEARANCE_GENRES.contains(genre.asInt())) {
                return null;
            }
        }
        return new PersonCredit(node.path("id").asLong(), type, title, text(node, "poster_path"),
                yearOf(firstText(node, "release_date", "first_air_date")), node.path("vote_average").asDouble(0), role);
    }

    private static String key(PersonCredit credit) {
        return credit.type() + "-" + credit.id();
    }

    private static PersonCredit withRole(PersonCredit c, String role) {
        return new PersonCredit(c.id(), c.type(), c.title(), c.posterPath(), c.year(), c.rating(), role);
    }

    private static String joinRoles(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null || a.contains(b) ? a : a + ", " + b;
    }

    private static List<PersonCredit> newestFirst(Map<String, PersonCredit> credits) {
        return credits.values().stream()
                .sorted(Comparator.comparing(PersonCredit::year, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(MAX_CREDITS)
                .toList();
    }

    // ---- where to watch ----

    /**
     * One country's streaming availability out of TMDB's {@code /watch/providers} answer, which holds every country under
     * {@code results}. TMDB splits services into flatrate (subscription), free, ads (free with adverts), rent and buy;
     * free and ads are merged into one "free" group. A service appears once per group, most prominent first (TMDB's
     * {@code display_priority}). A country with no data gives empty groups and a null link, never an error.
     */
    public WatchProviders providers(JsonNode root, String region) {
        JsonNode country = root.path("results").path(region);
        List<WatchProvider> free = providerGroup(country.path("free"));
        for (WatchProvider ads : providerGroup(country.path("ads"))) {
            if (free.stream().noneMatch(p -> p.id() == ads.id())) {
                free.add(ads);
            }
        }
        return new WatchProviders(region, text(country, "link"), providerGroup(country.path("flatrate")), free,
                providerGroup(country.path("rent")), providerGroup(country.path("buy")));
    }

    private List<WatchProvider> providerGroup(JsonNode array) {
        List<JsonNode> nodes = new ArrayList<>();
        for (JsonNode node : array) {
            if (node.path("provider_id").asInt(0) > 0 && text(node, "provider_name") != null) {
                nodes.add(node);
            }
        }
        nodes.sort(Comparator.comparingInt(n -> n.path("display_priority").asInt(Integer.MAX_VALUE)));
        List<WatchProvider> result = new ArrayList<>();
        for (JsonNode node : nodes) {
            int id = node.path("provider_id").asInt();
            if (result.stream().noneMatch(p -> p.id() == id)) {
                result.add(new WatchProvider(id, text(node, "provider_name"), text(node, "logo_path")));
            }
        }
        return result;
    }

    /** Text of a field, or null when it is missing, null or blank. */
    static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String s = value.asString("");
        return s.isBlank() ? null : s;
    }

    private static String firstText(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer yearOf(String date) {
        if (date == null || date.length() < 4) {
            return null;
        }
        try {
            return Integer.parseInt(date.substring(0, 4));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static Integer positiveOrNull(int value) {
        return value > 0 ? value : null;
    }

    private static Long positiveLongOrNull(long value) {
        return value > 0 ? value : null;
    }
}
