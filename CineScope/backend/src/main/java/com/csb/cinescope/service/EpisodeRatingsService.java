package com.csb.cinescope.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import org.springframework.stereotype.Service;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.EpisodeHeatmap;
import com.csb.cinescope.tmdb.TmdbGateway;
import com.csb.cinescope.tmdb.TmdbQuery;

import tools.jackson.databind.JsonNode;

/**
 * The episode-rating heatmap of a show: for each season (TMDB has one request per season), every episode with its average vote.
 * The show itself is asked once for its list of seasons, then the seasons are fetched in parallel on the shared catalogue thread
 * pool; every answer is cached for an hour by {@code tmdb/TmdbGateway}. Season 0 ("Specials") is left out because it is not part of
 * the story's run. A season that TMDB cannot supply is marked unavailable instead of failing the whole page.
 *
 * <p>A rating of 0, or an episode nobody has voted on, is "not rated yet" (null), never a zero: it would otherwise drag every average
 * down and show as the worst episode. Called by {@code controller/EpisodeController}; tested by {@code EpisodeApiTest}.
 */
@Service
public class EpisodeRatingsService {

    /** A show with more seasons than this is cut; the page shows a grid of them and a runaway show must not cost dozens of requests. */
    static final int MAX_SEASONS = 30;

    private final TmdbGateway tmdb;
    private final CineScopeProperties properties;
    private final ExecutorService executor;

    public EpisodeRatingsService(TmdbGateway tmdb, CineScopeProperties properties, ExecutorService catalogExecutor) {
        this.tmdb = tmdb;
        this.properties = properties;
        this.executor = catalogExecutor;
    }

    public EpisodeHeatmap forShow(long id) {
        JsonNode show = tmdb.detail("/tv/" + id, language());
        List<Integer> numbers = new ArrayList<>();
        for (JsonNode season : show.path("seasons")) {
            int number = season.path("season_number").asInt(0);
            if (number >= 1 && !numbers.contains(number)) {
                numbers.add(number);
            }
        }
        numbers.sort(Comparator.naturalOrder());
        boolean truncated = numbers.size() > MAX_SEASONS;
        List<Integer> shown = truncated ? numbers.subList(0, MAX_SEASONS) : numbers;

        LocalDate today = LocalDate.now();
        List<CompletableFuture<EpisodeHeatmap.Season>> futures = shown.stream()
                .map(number -> CompletableFuture.supplyAsync(() -> season(id, number, today), executor))
                .toList();
        List<EpisodeHeatmap.Season> seasons = futures.stream().map(CompletableFuture::join).toList();

        List<EpisodeHeatmap.Episode> rated = seasons.stream().flatMap(s -> s.episodes().stream()).filter(e -> e.rating() != null).toList();
        int episodes = seasons.stream().mapToInt(s -> s.episodes().size()).sum();
        Double average = rated.isEmpty() ? null : round(rated.stream().mapToDouble(EpisodeHeatmap.Episode::rating).average().orElse(0));
        EpisodeHeatmap.Episode best = null;
        EpisodeHeatmap.Episode worst = null;
        if (rated.size() >= 2) {
            // On a tie the best is the one more people have rated (its score is the more trustworthy). The worst is chosen from the
            // others, so one episode can never be both, even when every episode has the same score; there a tie goes to the earlier one.
            best = rated.stream().max(Comparator.comparingDouble(EpisodeHeatmap.Episode::rating).thenComparingInt(EpisodeHeatmap.Episode::votes)).orElse(null);
            EpisodeHeatmap.Episode chosen = best;
            worst = rated.stream().filter(e -> e != chosen)
                    .min(Comparator.comparingDouble(EpisodeHeatmap.Episode::rating).thenComparingInt(EpisodeHeatmap.Episode::season).thenComparingInt(EpisodeHeatmap.Episode::number))
                    .orElse(null);
        }
        String name = text(show, "name");
        return new EpisodeHeatmap(id, name != null ? name : "Untitled", seasons.size(), episodes, rated.size(), average, best, worst, truncated, seasons);
    }

    private EpisodeHeatmap.Season season(long showId, int number, LocalDate today) {
        try {
            JsonNode root = tmdb.detail("/tv/" + showId + "/season/" + number, language());
            List<EpisodeHeatmap.Episode> episodes = new ArrayList<>();
            for (JsonNode node : root.path("episodes")) {
                int votes = node.path("vote_count").asInt(0);
                double vote = node.path("vote_average").asDouble(0);
                String airDate = text(node, "air_date");
                String name = text(node, "name");
                episodes.add(new EpisodeHeatmap.Episode(number, node.path("episode_number").asInt(episodes.size() + 1), name != null ? name : "Episode " + (episodes.size() + 1),
                        airDate, votes > 0 && vote > 0 ? round(vote) : null, Math.max(votes, 0), isAired(airDate, today)));
            }
            episodes.sort(Comparator.comparingInt(EpisodeHeatmap.Episode::number));
            List<EpisodeHeatmap.Episode> rated = episodes.stream().filter(e -> e.rating() != null).toList();
            Double average = rated.isEmpty() ? null : round(rated.stream().mapToDouble(EpisodeHeatmap.Episode::rating).average().orElse(0));
            return new EpisodeHeatmap.Season(number, average, false, episodes);
        } catch (RuntimeException ex) {
            return new EpisodeHeatmap.Season(number, null, true, List.of()); // this season is unavailable right now; the others still show
        }
    }

    private SortedMap<String, String> language() {
        return TmdbQuery.create().with("language", properties.language()).build();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() && !value.asString().isBlank() ? value.asString() : null;
    }

    private static boolean isAired(String date, LocalDate today) {
        try {
            return date != null && !LocalDate.parse(date).isAfter(today);
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
