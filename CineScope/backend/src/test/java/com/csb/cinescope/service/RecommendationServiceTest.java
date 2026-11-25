package com.csb.cinescope.service;

import static com.csb.cinescope.TmdbFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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
import com.csb.cinescope.dto.Recommendation;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.exception.UpstreamException;
import com.csb.cinescope.mapper.TitleMapper;
import com.csb.cinescope.repository.UserRepository;
import com.csb.cinescope.repository.WatchlistItemRepository;
import com.csb.cinescope.tmdb.TmdbGateway;

import tools.jackson.databind.JsonNode;

/**
 * Unit tests of {@link RecommendationService} with every collaborator mocked: how wishes become a TMDB query, how the
 * pick is drawn (repeatable per seed), what a signed-in user's list changes, and that a failing extra never fails the
 * whole answer. No Spring context, no network, no database.
 */
@ExtendWith(MockitoExtension.class)
class RecommendationServiceTest {

    @Mock TmdbGateway tmdb;
    @Mock CatalogService catalog;
    @Mock UserRepository users;
    @Mock WatchlistItemRepository watchlist;

    ExecutorService executor = Executors.newFixedThreadPool(4);
    RecommendationService service;

    @BeforeEach
    void setUp() {
        service = new RecommendationService(tmdb, new TitleMapper(), new CineScopeProperties("IN", "en-US"), catalog, users, watchlist, executor);
    }

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    /** A TMDB discover page holding these ids, all with the same rating and a poster. */
    private static JsonNode page(double rating, long... ids) {
        StringBuilder items = new StringBuilder();
        for (long id : ids) {
            items.append(items.length() > 0 ? "," : "")
                    .append("{\"id\":").append(id).append(",\"title\":\"T").append(id).append("\",\"overview\":\"o\",\"poster_path\":\"/p")
                    .append(id).append(".jpg\",\"release_date\":\"2024-01-01\",\"vote_average\":").append(rating).append("}");
        }
        return json("{\"page\":1,\"total_pages\":9,\"results\":[" + items + "]}");
    }

    private void tmdbAlwaysReturns(JsonNode page) {
        when(tmdb.lists(eq("/discover/movie"), any())).thenReturn(page);
    }

    private static WatchlistItemRepository.Tagged tagged(long id, WatchStatus status) {
        return new WatchlistItemRepository.Tagged() {
            public Long getTmdbId() {
                return id;
            }

            public WatchStatus getStatus() {
                return status;
            }
        };
    }

    private void signedInWith(WatchlistItemRepository.Tagged... items) {
        User ravi = mock(User.class);
        when(users.findByUsername("ravi")).thenReturn(Optional.of(ravi));
        when(watchlist.findTagged(ravi, "movie")).thenReturn(List.of(items));
    }

    // ---- validation ----

    @Test
    void invalidWishesAreRejectedBeforeTmdbIsCalled() {
        assertThrows(BadRequestException.class, () -> service.tonight(null, "book", null, null, null, false, 1L));
        assertThrows(BadRequestException.class, () -> service.tonight(null, "movie", "zzz", null, null, false, 1L));
        assertThrows(BadRequestException.class, () -> service.tonight(null, "movie", null, 10, null, false, 1L));
        assertThrows(BadRequestException.class, () -> service.tonight(null, "movie", null, 500, null, false, 1L));
        assertThrows(BadRequestException.class, () -> service.tonight(null, "movie", null, null, "english", false, 1L));
        verifyNoInteractions(tmdb, users, watchlist, catalog);
    }

    // ---- the TMDB query ----

    @SuppressWarnings("unchecked")
    private List<SortedMap<String, String>> queriesSent(String path, int times) {
        ArgumentCaptor<SortedMap<String, String>> query = ArgumentCaptor.forClass(SortedMap.class);
        verify(tmdb, times(times)).lists(eq(path), query.capture());
        return query.getAllValues();
    }

    @Test
    void wishesBecomeOneDiscoverQueryPerPageForTwoPages() {
        tmdbAlwaysReturns(page(8, 1));

        service.tonight(null, "movie", "thrill", 120, "HI", true, 1L);

        List<SortedMap<String, String>> sent = queriesSent("/discover/movie", 2);
        SortedMap<String, String> first = sent.get(0);
        assertEquals("1", first.get("page"));
        assertEquals("2", sent.get(1).get("page"));
        assertEquals("53|80", first.get("with_genres"));
        assertEquals("120", first.get("with_runtime.lte"));
        assertEquals("hi", first.get("with_original_language"));
        assertEquals("IN", first.get("watch_region"));
        assertEquals("flatrate|free|ads", first.get("with_watch_monetization_types"));
        assertEquals("popularity.desc", first.get("sort_by"));
        assertEquals("6.5", first.get("vote_average.gte"));
        assertEquals("false", first.get("include_adult"));
    }

    @Test
    void tvUsesTvGenresAndNoStreamingFilterUnlessAsked() {
        when(tmdb.lists(eq("/discover/tv"), any())).thenReturn(json("{\"results\":[]}"));

        service.tonight(null, "tv", "thrill", null, null, false, 1L);

        SortedMap<String, String> first = queriesSent("/discover/tv", 2).get(0);
        assertEquals("80|9648", first.get("with_genres"));
        assertFalse(first.containsKey("watch_region"));
        assertFalse(first.containsKey("with_runtime.lte"));
        assertFalse(first.containsKey("with_original_language"));
    }

    @Test
    void noMoodMeansNoGenreFilter() {
        tmdbAlwaysReturns(page(8, 1));

        service.tonight(null, "movie", null, null, null, false, 1L);

        assertFalse(queriesSent("/discover/movie", 2).get(0).containsKey("with_genres"));
    }

    // ---- drawing the pick ----

    @Test
    void theSameSeedGivesTheSameAnswerAndOtherSeedsVary() {
        tmdbAlwaysReturns(page(7, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10));

        long first = service.tonight(null, "movie", null, null, null, false, 7L).pick().id();
        long again = service.tonight(null, "movie", null, null, null, false, 7L).pick().id();
        Set<Long> picks = new HashSet<>();
        for (long seed = 1; seed <= 40; seed++) {
            picks.add(service.tonight(null, "movie", null, null, null, false, seed).pick().id());
        }

        assertEquals(first, again);
        assertTrue(picks.size() > 1, "different seeds should not always give the same title");
    }

    @Test
    void theAnswerHasOnePickFiveAlternativesAndEchoesTheSeed() {
        tmdbAlwaysReturns(page(7, 1, 2, 3, 4, 5, 6, 7, 8));

        Recommendation r = service.tonight(null, "movie", null, null, null, false, 99L);

        assertEquals(99L, r.seed());
        assertEquals(8, r.considered());
        assertEquals(5, r.alternatives().size());
        assertFalse(r.alternatives().stream().anyMatch(a -> a.id() == r.pick().id()), "the pick is not repeated as an alternative");
    }

    @Test
    void aMuchBetterRatedTitleBeatsTheRandomBonus() {
        when(tmdb.lists(eq("/discover/movie"), any())).thenReturn(json("""
                {"results":[{"id":1,"title":"Low","poster_path":"/a.jpg","vote_average":6.6},
                            {"id":2,"title":"High","poster_path":"/b.jpg","vote_average":9.5}]}"""));

        for (long seed = 1; seed <= 30; seed++) {
            assertEquals(2, service.tonight(null, "movie", null, null, null, false, seed).pick().id());
        }
    }

    // ---- candidates ----

    @Test
    void titlesWithoutAPosterAreDropped() {
        when(tmdb.lists(eq("/discover/movie"), any())).thenReturn(json("""
                {"results":[{"id":1,"title":"No poster","vote_average":9},{"id":2,"title":"Has poster","poster_path":"/b.jpg","vote_average":7}]}"""));

        Recommendation r = service.tonight(null, "movie", null, null, null, false, 1L);

        assertEquals(1, r.considered());
        assertEquals(2, r.pick().id());
    }

    @Test
    void theSameTitleOnBothPagesCountsOnce() {
        tmdbAlwaysReturns(page(7, 1, 2)); // both pages return the same two titles

        assertEquals(2, service.tonight(null, "movie", null, null, null, false, 1L).considered());
    }

    @Test
    void nothingMatchingGivesNoPickAndNoExtraLookups() {
        tmdbAlwaysReturns(json("{\"results\":[]}"));

        Recommendation r = service.tonight(null, "movie", "scary", 30, "ko", true, 5L);

        assertNull(r.pick());
        assertTrue(r.alternatives().isEmpty() && r.reasons().isEmpty());
        assertEquals(0, r.considered());
        assertEquals(5L, r.seed());
        verifyNoInteractions(catalog);
    }


    // ---- time limit (TMDB's own runtime filter cannot be trusted) ----

    /** A detail answer with this runtime. Built before any {@code when(...)} that uses it (Mockito forbids nesting). */
    private static TitleDetail detailWithRuntime(Integer runtime) {
        TitleDetail d = mock(TitleDetail.class);
        when(d.runtime()).thenReturn(runtime);
        return d;
    }

    private void stubRuntime(long id, Integer runtime) {
        TitleDetail d = detailWithRuntime(runtime);
        when(catalog.detail("movie", id)).thenReturn(d);
    }

    private void stubRuntimeForAll(Integer runtime) {
        TitleDetail d = detailWithRuntime(runtime);
        when(catalog.detail(eq("movie"), anyLong())).thenReturn(d);
    }

    @Test
    void titlesLongerThanTheLimitAreDroppedEvenIfTmdbLetThemThrough() {
        tmdbAlwaysReturns(page(8, 1, 2, 3, 4));
        stubRuntime(1, 169);
        stubRuntime(2, 95);
        stubRuntime(3, 121);
        stubRuntime(4, 120);

        for (long seed = 1; seed <= 15; seed++) {
            Recommendation r = service.tonight(null, "movie", null, 120, null, false, seed);
            assertEquals(2, r.considered());
            assertTrue(Set.of(2L, 4L).contains(r.pick().id()), "pick must be one of the short titles");
            assertTrue(r.alternatives().stream().allMatch(a -> a.id() == 2 || a.id() == 4));
        }
    }

    @Test
    void ifEveryCandidateIsTooLongNothingMatches() {
        tmdbAlwaysReturns(page(8, 1, 2));
        stubRuntimeForAll(180);

        Recommendation r = service.tonight(null, "movie", null, 90, null, false, 1L);

        assertNull(r.pick());
        assertEquals(0, r.considered());
    }

    @Test
    void anUnknownRuntimeOrAFailedLookupDoesNotRejectATitle() {
        tmdbAlwaysReturns(page(8, 1, 2, 3));
        stubRuntime(1, null);
        when(catalog.detail("movie", 2)).thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "x"));
        stubRuntime(3, 300);

        Recommendation r = service.tonight(null, "movie", null, 100, null, false, 1L);

        assertEquals(2, r.considered()); // 1 (unknown) and 2 (lookup failed) stay; 3 is far too long
    }

    @Test
    void onlyTheBestRankedCandidatesAreLookedUp_andNothingIsLookedUpWithoutALimit() {
        long[] ids = new long[40];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = i + 1;
        }
        tmdbAlwaysReturns(page(8, ids));
        stubRuntimeForAll(90);

        Recommendation limited = service.tonight(null, "movie", null, 120, null, false, 1L);
        assertEquals(16, limited.considered());
        verify(catalog, times(16 + 1)).detail(eq("movie"), anyLong()); // 16 checks + the pick's own details (a cached lookup in real life)

        org.mockito.Mockito.clearInvocations(catalog);
        service.tonight(null, "movie", null, null, null, false, 1L);
        verify(catalog, times(1)).detail(eq("movie"), anyLong()); // only the pick's details
        verify(catalog, never()).detail(eq("tv"), anyLong());
    }

    // ---- the signed-in user ----

    @Test
    void watchedAndWatchingTitlesAreSkippedAndWantedOnesWin() {
        tmdbAlwaysReturns(page(8, 1, 2, 3, 4, 5));
        signedInWith(tagged(1, WatchStatus.WANT), tagged(3, WatchStatus.WATCHED), tagged(4, WatchStatus.WATCHING));

        for (long seed = 1; seed <= 20; seed++) {
            Recommendation r = service.tonight("ravi", "movie", null, null, null, false, seed);
            assertEquals(1, r.pick().id(), "the wanted title gets a boost larger than the random bonus");
            assertTrue(r.pick().onWatchlist());
            assertTrue(r.reasons().contains("On your watchlist"));
            assertEquals(3, r.considered()); // 1, 2 and 5; 3 and 4 were seen
            assertTrue(r.alternatives().stream().noneMatch(a -> a.id() == 3 || a.id() == 4));
            assertTrue(r.alternatives().stream().noneMatch(a -> a.onWatchlist()));
        }
    }

    @Test
    void aVisitorNeverTouchesTheUserTables() {
        tmdbAlwaysReturns(page(8, 1));

        service.tonight(null, "movie", null, null, null, false, 1L);

        verifyNoInteractions(users, watchlist);
    }

    @Test
    void anUnknownUsernameIsTreatedLikeAVisitor() {
        tmdbAlwaysReturns(page(8, 1));
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertEquals(1, service.tonight("ghost", "movie", null, null, null, false, 1L).pick().id());
        verifyNoInteractions(watchlist);
    }

    // ---- failures ----

    @Test
    void aFailingSecondPageOnlyMeansFewerCandidates() {
        when(tmdb.lists(eq("/discover/movie"), any()))
                .thenReturn(page(8, 1, 2))
                .thenThrow(new UpstreamException(HttpStatus.GATEWAY_TIMEOUT, "slow"));

        Recommendation r = service.tonight(null, "movie", null, null, null, false, 1L);

        assertEquals(2, r.considered());
        assertNotNull(r.pick());
    }

    @Test
    void aFailingFirstPageIsReportedToTheCaller() {
        when(tmdb.lists(eq("/discover/movie"), any())).thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "down"));

        assertThrows(UpstreamException.class, () -> service.tonight(null, "movie", null, null, null, false, 1L));
    }

    @Test
    void failingDetailsAndProvidersLeaveThemNullButKeepThePick() {
        tmdbAlwaysReturns(page(8, 1));
        when(catalog.detail(eq("movie"), anyLong())).thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "x"));
        when(catalog.providers(eq("movie"), anyLong(), any())).thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "x"));

        Recommendation r = service.tonight(null, "movie", null, null, null, false, 1L);

        assertEquals(1, r.pick().id());
        assertNull(r.details());
        assertNull(r.providers());
    }

    @Test
    void theReasonsDescribeTheWishesInOrder() {
        tmdbAlwaysReturns(page(8, 1));

        Recommendation r = service.tonight(null, "movie", "laugh", 90, "hi", true, 1L);

        assertEquals(List.of("Make me laugh", "Up to 90 min", "Language: HI", "Streaming in IN", "Rated 8.0"), r.reasons());
        verify(catalog, atLeastOnce()).detail("movie", 1);
        verify(catalog).providers(eq("movie"), eq(1L), any());
    }

    @Test
    void tvRuntimeReasonSaysPerEpisode() {
        when(tmdb.lists(eq("/discover/tv"), any())).thenReturn(json("""
                {"results":[{"id":5,"name":"Show","poster_path":"/s.jpg","vote_average":8}]}"""));

        assertTrue(service.tonight(null, "tv", null, 45, null, false, 1L).reasons().contains("Up to 45 min per episode"));
    }

    // ---- family-safe (only titles rated for everyone) ----

    private static TitleDetail detailRated(boolean familyFriendly) {
        TitleDetail d = mock(TitleDetail.class);
        when(d.familyFriendly()).thenReturn(familyFriendly);
        return d;
    }

    private void stubFamily(long id, boolean familyFriendly) {
        TitleDetail d = detailRated(familyFriendly);
        when(catalog.detail("movie", id)).thenReturn(d);
    }

    @Test
    void familySafeKeepsOnlyTitlesRatedForEveryone() {
        tmdbAlwaysReturns(page(8, 1, 2, 3, 4));
        stubFamily(1, true);
        stubFamily(2, false);
        stubFamily(3, true);
        stubFamily(4, false);

        for (long seed = 1; seed <= 15; seed++) {
            Recommendation r = service.tonight(null, "movie", null, null, null, false, true, seed);
            assertEquals(2, r.considered());
            assertTrue(Set.of(1L, 3L).contains(r.pick().id()), "pick must be one of the family titles");
            assertTrue(r.alternatives().stream().allMatch(a -> a.id() == 1 || a.id() == 3));
            assertTrue(r.reasons().contains("Suitable for all ages"));
        }
    }

    @Test
    void familySafeDropsATitleWhoseRatingCannotBeLookedUp() {
        tmdbAlwaysReturns(page(8, 1, 2));
        stubFamily(1, true);
        when(catalog.detail("movie", 2)).thenThrow(new UpstreamException(HttpStatus.BAD_GATEWAY, "x")); // unknown is not safe

        for (long seed = 1; seed <= 10; seed++) {
            Recommendation r = service.tonight(null, "movie", null, null, null, false, true, seed);
            assertEquals(1, r.considered());
            assertEquals(1L, r.pick().id());
        }
    }

    @Test
    void ifNothingIsRatedForEveryoneNothingMatches() {
        tmdbAlwaysReturns(page(8, 1, 2));
        TitleDetail unsafe = detailRated(false);
        when(catalog.detail(eq("movie"), anyLong())).thenReturn(unsafe);

        Recommendation r = service.tonight(null, "movie", null, null, null, false, true, 1L);

        assertNull(r.pick());
        assertEquals(0, r.considered());
    }

    @Test
    void withoutTheSwitchRatingsAreNotConsidered() {
        tmdbAlwaysReturns(page(8, 1, 2, 3));
        TitleDetail unsafe = mock(TitleDetail.class); // its rating is never read: the switch is off
        when(catalog.detail(eq("movie"), anyLong())).thenReturn(unsafe);

        Recommendation r = service.tonight(null, "movie", null, null, null, false, 1L);

        assertEquals(3, r.considered());
        assertFalse(r.reasons().contains("Suitable for all ages"));
    }

    @Test
    void familySafeAndATimeLimitWorkTogether() {
        tmdbAlwaysReturns(page(8, 1, 2, 3, 4));
        // 1: family and short, 2: family but long, 3: short but not family, 4: neither
        TitleDetail one = mock(TitleDetail.class);
        when(one.familyFriendly()).thenReturn(true);
        when(one.runtime()).thenReturn(90);
        TitleDetail two = mock(TitleDetail.class);
        lenient().when(two.familyFriendly()).thenReturn(true);
        when(two.runtime()).thenReturn(170);
        TitleDetail three = mock(TitleDetail.class);
        lenient().when(three.familyFriendly()).thenReturn(false);
        when(three.runtime()).thenReturn(90);
        TitleDetail four = mock(TitleDetail.class);
        lenient().when(four.familyFriendly()).thenReturn(false);
        when(four.runtime()).thenReturn(170);
        when(catalog.detail("movie", 1)).thenReturn(one);
        when(catalog.detail("movie", 2)).thenReturn(two);
        when(catalog.detail("movie", 3)).thenReturn(three);
        when(catalog.detail("movie", 4)).thenReturn(four);

        Recommendation r = service.tonight(null, "movie", null, 120, null, false, true, 1L);

        assertEquals(1, r.considered());
        assertEquals(1L, r.pick().id());
    }
}
