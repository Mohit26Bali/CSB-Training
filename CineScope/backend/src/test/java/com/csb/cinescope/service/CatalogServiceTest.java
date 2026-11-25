package com.csb.cinescope.service;

import static com.csb.cinescope.TmdbFixtures.GENRES_MOVIE;
import static com.csb.cinescope.TmdbFixtures.MOVIE_DETAIL;
import static com.csb.cinescope.TmdbFixtures.POPULAR_MOVIES;
import static com.csb.cinescope.TmdbFixtures.SEARCH_MULTI;
import static com.csb.cinescope.TmdbFixtures.TRENDING_ALL;
import static com.csb.cinescope.TmdbFixtures.TV_DETAIL;
import static com.csb.cinescope.TmdbFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.SortedMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.HomeResponse;
import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.SearchItem;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.TitleSummary;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.UpstreamException;
import com.csb.cinescope.mapper.TitleMapper;
import com.csb.cinescope.tmdb.TmdbGateway;

/**
 * Unit tests of {@link CatalogService} with a mocked {@link TmdbGateway}: which TMDB path and parameters each use case
 * asks for, that invalid requests are rejected before TMDB is called at all, and how the home page copes with failing
 * rows. No Spring context, no network.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock TmdbGateway tmdb;

    ExecutorService executor = Executors.newFixedThreadPool(4);
    CatalogService service;

    @BeforeEach
    void setUp() {
        service = new CatalogService(tmdb, new TitleMapper(), new CineScopeProperties("IN", "en-US"), executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @SuppressWarnings("unchecked")
    private SortedMap<String, String> capturedQuery(String method, String path) {
        ArgumentCaptor<SortedMap<String, String>> query = ArgumentCaptor.forClass(SortedMap.class);
        switch (method) {
            case "lists" -> verify(tmdb).lists(eq(path), query.capture());
            case "detail" -> verify(tmdb).detail(eq(path), query.capture());
            default -> verify(tmdb).reference(eq(path), query.capture());
        }
        return query.getValue();
    }

    // ---- lists ----

    @Test
    void aMovieListAsksTmdbForTheRightPathInTheDefaultRegionAndLanguage() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(POPULAR_MOVIES));

        PageResult<TitleSummary> page = service.list("popular", "movie", 2);

        SortedMap<String, String> query = capturedQuery("lists", "/movie/popular");
        assertEquals("IN", query.get("region"));
        assertEquals("en-US", query.get("language"));
        assertEquals("2", query.get("page"));
        assertEquals(2, page.items().size());
    }

    @Test
    void trendingUsesTheWeeklyTrendingPath_andShowsAreNotRegionFiltered() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(TRENDING_ALL));

        service.list("trending", "all", 1);
        assertFalse(capturedQuery("lists", "/trending/all/week").containsKey("region"));
    }

    @Test
    void aTvListHasNoRegionParameter() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(POPULAR_MOVIES));

        service.list("top_rated", "tv", 1);
        assertFalse(capturedQuery("lists", "/tv/top_rated").containsKey("region"));
    }

    @Test
    void invalidListRequestsAreRejectedBeforeTmdbIsCalled() {
        assertThrows(BadRequestException.class, () -> service.list("bogus", "movie", 1));
        assertThrows(BadRequestException.class, () -> service.list("popular", "book", 1));
        assertThrows(BadRequestException.class, () -> service.list("popular", "all", 1)); // "all" is for trending only
        assertThrows(BadRequestException.class, () -> service.list("now_playing", "tv", 1)); // movies only
        assertThrows(BadRequestException.class, () -> service.list("on_the_air", "movie", 1)); // tv only
        assertThrows(BadRequestException.class, () -> service.list("popular", "movie", 0));
        assertThrows(BadRequestException.class, () -> service.list("popular", "movie", 501));

        verifyNoInteractions(tmdb);
    }

    // ---- discover ----

    @Test
    void discoverPassesGenreYearAndRatingFiltersToTmdb() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(POPULAR_MOVIES));

        service.discover("movie", 28, 2023, 7.0, "rating", 1);

        SortedMap<String, String> query = capturedQuery("lists", "/discover/movie");
        assertEquals("28", query.get("with_genres"));
        assertEquals("2023", query.get("primary_release_year"));
        assertEquals("7.0", query.get("vote_average.gte"));
        assertEquals("vote_average.desc", query.get("sort_by"));
        assertEquals("300", query.get("vote_count.gte")); // a rating sort needs many votes, or obscure 10/10 titles win
        assertEquals("false", query.get("include_adult"));
    }

    @Test
    void discoverUsesTheShowSpecificYearParameterForTv() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(POPULAR_MOVIES));

        service.discover("tv", null, 2020, null, "popularity", 1);

        SortedMap<String, String> query = capturedQuery("lists", "/discover/tv");
        assertEquals("2020", query.get("first_air_date_year"));
        assertEquals("popularity.desc", query.get("sort_by"));
        assertFalse(query.containsKey("with_genres"));
    }

    @Test
    void sortingByNewestExcludesTitlesThatHaveNotBeenReleasedYet() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(POPULAR_MOVIES));

        service.discover("movie", null, null, null, "newest", 1);

        SortedMap<String, String> query = capturedQuery("lists", "/discover/movie");
        assertEquals("primary_release_date.desc", query.get("sort_by"));
        assertEquals(LocalDate.now().toString(), query.get("primary_release_date.lte"));
    }

    @Test
    void invalidDiscoverRequestsAreRejectedBeforeTmdbIsCalled() {
        assertThrows(BadRequestException.class, () -> service.discover("book", null, null, null, "popularity", 1));
        assertThrows(BadRequestException.class, () -> service.discover("movie", null, null, null, "zzz", 1));
        assertThrows(BadRequestException.class, () -> service.discover("movie", null, 1500, null, "popularity", 1));
        assertThrows(BadRequestException.class, () -> service.discover("movie", null, null, 11.0, "popularity", 1));
        assertThrows(BadRequestException.class, () -> service.discover("movie", null, null, -1.0, "popularity", 1));
        assertThrows(BadRequestException.class, () -> service.discover("movie", null, null, null, "popularity", 0));

        verifyNoInteractions(tmdb);
    }

    // ---- search ----

    @Test
    void searchTrimsTheTextAndHidesAdultContent() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(SEARCH_MULTI));

        PageResult<SearchItem> page = service.search("  nolan  ", 1);

        SortedMap<String, String> query = capturedQuery("lists", "/search/multi");
        assertEquals("nolan", query.get("query"));
        assertEquals("false", query.get("include_adult"));
        assertEquals(3, page.items().size());
    }

    @Test
    void blankOrTooLongSearchTextIsRejected() {
        assertThrows(BadRequestException.class, () -> service.search("", 1));
        assertThrows(BadRequestException.class, () -> service.search("   ", 1));
        assertThrows(BadRequestException.class, () -> service.search(null, 1));
        assertThrows(BadRequestException.class, () -> service.search("x".repeat(101), 1));
        verifyNoInteractions(tmdb);
    }

    // ---- details and genres ----

    @Test
    void aMovieDetailAsksForCreditsVideosAndReleaseDates() {
        when(tmdb.detail(anyString(), any())).thenReturn(json(MOVIE_DETAIL));

        TitleDetail detail = service.detail("movie", 550);

        String appended = capturedQuery("detail", "/movie/550").get("append_to_response");
        assertTrue(appended.contains("credits") && appended.contains("videos") && appended.contains("release_dates"));
        assertEquals("A", detail.certification()); // the default region is India
    }

    @Test
    void aTvDetailAsksForContentRatingsInsteadOfReleaseDates() {
        when(tmdb.detail(anyString(), any())).thenReturn(json(TV_DETAIL));

        service.detail("tv", 1396);

        String appended = capturedQuery("detail", "/tv/1396").get("append_to_response");
        assertTrue(appended.contains("content_ratings"));
        assertFalse(appended.contains("release_dates"));
    }

    @Test
    void invalidDetailRequestsAreRejectedBeforeTmdbIsCalled() {
        assertThrows(BadRequestException.class, () -> service.detail("book", 1));
        assertThrows(BadRequestException.class, () -> service.detail("movie", 0));
        assertThrows(BadRequestException.class, () -> service.detail("movie", -5));
        verifyNoInteractions(tmdb);
    }

    @Test
    void genresAreFetchedAsReferenceData() {
        when(tmdb.reference(anyString(), any())).thenReturn(json(GENRES_MOVIE));

        assertEquals(2, service.genres("movie").size());
        capturedQuery("reference", "/genre/movie/list");
        assertThrows(BadRequestException.class, () -> service.genres("book"));
    }

    // ---- home ----

    @Test
    void theHomePageHasEightRowsAndTheFirstTrendingTitleWithAPictureAsHero() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(TRENDING_ALL));

        HomeResponse home = service.home();

        assertEquals(8, home.rows().size());
        assertEquals("trending-today", home.rows().get(0).key());
        assertEquals("trending", home.rows().get(1).key());
        assertEquals(home.hero(), home.heroes().get(0));
        assertEquals("The Last Lighthouse", home.hero().title()); // has backdrop and overview
        assertEquals("/b101.jpg", home.hero().backdropPath());
    }

    @Test
    void aFailingRowIsLeftOutInsteadOfBreakingTheWholePage() {
        when(tmdb.lists(anyString(), any())).thenAnswer(call -> {
            if (call.getArgument(0, String.class).equals("/tv/popular")) {
                throw new UpstreamException(HttpStatus.BAD_GATEWAY, "boom");
            }
            return json(POPULAR_MOVIES);
        });

        HomeResponse home = service.home();

        assertEquals(7, home.rows().size());
        assertFalse(home.rows().stream().anyMatch(r -> r.key().equals("popular-tv")));
    }

    @Test
    void whenEveryRowFailsTheErrorIsReported() {
        when(tmdb.lists(anyString(), any())).thenThrow(new UpstreamException(HttpStatus.SERVICE_UNAVAILABLE, "busy"));

        UpstreamException ex = assertThrows(UpstreamException.class, () -> service.home());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatus());
    }

    // ---- the rotating home banner and "trending today" ----

    /** A trending page of movies with these ids, all having a backdrop and a description. */
    private static String trending(int... ids) {
        StringBuilder items = new StringBuilder();
        for (int id : ids) {
            items.append(items.length() > 0 ? "," : "").append("{\"id\":").append(id).append(",\"media_type\":\"movie\",\"title\":\"T").append(id)
                    .append("\",\"overview\":\"o\",\"poster_path\":\"/p").append(id).append(".jpg\",\"backdrop_path\":\"/b").append(id)
                    .append(".jpg\",\"release_date\":\"2024-01-01\",\"vote_average\":7}");
        }
        return "{\"page\":1,\"total_pages\":1,\"results\":[" + items + "]}";
    }

    @Test
    void theBannerRotatesAtMostSixOfTodaysTrendingTitles() {
        when(tmdb.lists(anyString(), any())).thenAnswer(call -> json(call.getArgument(0, String.class).endsWith("/day")
                ? trending(1, 2, 3, 4, 5, 6, 7, 8) : trending(21, 22, 23)));

        HomeResponse home = service.home();

        assertEquals(java.util.List.of(1L, 2L, 3L, 4L, 5L, 6L), home.heroes().stream().map(TitleSummary::id).toList());
        assertEquals(1L, home.hero().id());
    }

    @Test
    void whenTodayHasFewTitlesTheBannerIsToppedUpFromThisWeekWithoutRepeats() {
        when(tmdb.lists(anyString(), any())).thenAnswer(call -> json(call.getArgument(0, String.class).endsWith("/day")
                ? trending(1, 2) : trending(2, 3, 4, 5, 6, 7)));

        HomeResponse home = service.home();

        assertEquals(java.util.List.of(1L, 2L, 3L, 4L, 5L, 6L), home.heroes().stream().map(TitleSummary::id).toList());
    }

    @Test
    void titlesWithoutABackdropOrDescriptionAreNotInTheBanner() {
        String mixed = "{\"results\":[{\"id\":1,\"media_type\":\"movie\",\"title\":\"No picture\",\"overview\":\"o\"},"
                + "{\"id\":2,\"media_type\":\"movie\",\"title\":\"No text\",\"backdrop_path\":\"/b2.jpg\"},"
                + "{\"id\":3,\"media_type\":\"movie\",\"title\":\"Good\",\"overview\":\"o\",\"backdrop_path\":\"/b3.jpg\"}]}";
        when(tmdb.lists(anyString(), any())).thenReturn(json(mixed));

        assertEquals(java.util.List.of(3L), service.home().heroes().stream().map(TitleSummary::id).toList());
    }

    @Test
    void trendingTodayUsesTheDayPathAndTrendingTheWeekPath() {
        when(tmdb.lists(anyString(), any())).thenReturn(json(TRENDING_ALL));

        service.list("trending_today", "all", 1);
        service.list("trending_today", "tv", 1);
        service.list("trending", "all", 1);

        verify(tmdb).lists(eq("/trending/all/day"), any());
        verify(tmdb).lists(eq("/trending/tv/day"), any());
        verify(tmdb).lists(eq("/trending/all/week"), any());
        assertThrows(BadRequestException.class, () -> service.list("trending_today", "book", 1));
    }
}
