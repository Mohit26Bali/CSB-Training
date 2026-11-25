package com.csb.cinescope.controller;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.TmdbFixtures;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;

/**
 * "What to watch tonight" through the real stack with only TMDB replaced: the request sent to TMDB for the wishes, the
 * JSON a browser receives (pick, details, where to watch, alternatives, reasons), the effect of a signed-in user's list,
 * the failures that must not break the answer and the ones that must. Open to visitors; the login only personalises.
 */
class TonightApiTest extends AbstractApiTest {

    private static String page(double rating, long... ids) {
        StringBuilder items = new StringBuilder();
        for (long id : ids) {
            items.append(items.length() > 0 ? "," : "").append("{\"id\":").append(id).append(",\"title\":\"T").append(id)
                    .append("\",\"overview\":\"o\",\"poster_path\":\"/p").append(id).append(".jpg\",\"release_date\":\"2024-01-01\",\"vote_average\":")
                    .append(rating).append("}");
        }
        return "{\"page\":1,\"total_pages\":9,\"results\":[" + items + "]}";
    }

    private void discoverPages(String firstPage, String secondPage) {
        tmdb.expect(once(), requestTo(Matchers.containsString("/discover/movie"))).andExpect(queryParam("page", "1"))
                .andRespond(withSuccess(firstPage, MediaType.APPLICATION_JSON));
        tmdb.expect(once(), requestTo(Matchers.containsString("/discover/movie"))).andExpect(queryParam("page", "2"))
                .andRespond(withSuccess(secondPage, MediaType.APPLICATION_JSON));
    }

    @Test
    void aVisitorGetsAPickWithDetailsAndWhereToWatchAndTmdbReceivesTheWishes() throws Exception {
        tmdb.expect(once(), requestTo(Matchers.containsString("/discover/movie")))
                .andExpect(queryParam("page", "1"))
                .andExpect(queryParam("with_genres", "35"))
                .andExpect(queryParam("with_runtime.lte", "150"))
                .andExpect(queryParam("with_original_language", "hi"))
                .andExpect(queryParam("watch_region", "IN"))
                .andExpect(queryParam("with_watch_monetization_types", "flatrate%7Cfree%7Cads"))
                .andRespond(withSuccess(page(8.1, 11), MediaType.APPLICATION_JSON));
        tmdb.expect(once(), requestTo(Matchers.containsString("/discover/movie"))).andExpect(queryParam("page", "2"))
                .andRespond(withSuccess("{\"results\":[]}", MediaType.APPLICATION_JSON));
        tmdbReturns("/movie/11?", TmdbFixtures.MOVIE_DETAIL);
        tmdbReturns("/movie/11/watch/providers", TmdbFixtures.PROVIDERS);

        mvc.perform(get("/api/tonight?type=movie&mood=laugh&maxMinutes=150&language=hi&streamingOnly=true&seed=5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick.id").value(11))
                .andExpect(jsonPath("$.pick.title").value("T11"))
                .andExpect(jsonPath("$.pick.onWatchlist").value(false))
                .andExpect(jsonPath("$.details.title").value("Fight Club"))
                .andExpect(jsonPath("$.providers.region").value("IN"))
                .andExpect(jsonPath("$.providers.stream[0].name").value("Netflix"))
                .andExpect(jsonPath("$.alternatives.length()").value(0))
                .andExpect(jsonPath("$.considered").value(1))
                .andExpect(jsonPath("$.seed").value(5))
                .andExpect(jsonPath("$.reasons[0]").value("Make me laugh"))
                .andExpect(jsonPath("$.reasons[3]").value("Streaming in IN"));
        tmdb.verify();
    }

    /** One full request for six equally rated candidates with this seed; returns the id of the title picked. */
    private long pickFor(long seed) throws Exception {
        tmdb.reset();
        cacheManager.getCacheNames().forEach(name -> cacheManager.getCache(name).clear());
        discoverPages(page(7, 1, 2, 3, 4, 5, 6), "{\"results\":[]}");
        tmdb.expect(once(), requestTo(Matchers.matchesPattern(".*/movie/\\d+\\?.*"))).andRespond(withSuccess(TmdbFixtures.MOVIE_DETAIL, MediaType.APPLICATION_JSON));
        tmdb.expect(once(), requestTo(Matchers.containsString("/watch/providers"))).andRespond(withSuccess(TmdbFixtures.PROVIDERS, MediaType.APPLICATION_JSON));
        String body = mvc.perform(get("/api/tonight?seed=" + seed)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) com.jayway.jsonpath.JsonPath.read(body, "$.pick.id")).longValue();
    }

    @Test
    void aTitleLongerThanTheTimeLimitIsLeftOutAfterCheckingItsRealRuntime() throws Exception {
        discoverPages(page(7, 11), "{\"results\":[]}");
        tmdbReturns("/movie/11?", TmdbFixtures.MOVIE_DETAIL); // runs 139 minutes; the details are then cached for the pick

        mvc.perform(get("/api/tonight?maxMinutes=120&seed=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick").doesNotExist())
                .andExpect(jsonPath("$.considered").value(0));
        tmdb.verify();
    }

    @Test
    void familySafeLeavesOutATitleRatedForAdults() throws Exception {
        discoverPages(page(7, 11), "{\"results\":[]}");
        tmdbReturns("/movie/11?", TmdbFixtures.MOVIE_DETAIL); // rated A in India, R in the US

        mvc.perform(get("/api/tonight?familySafe=true&seed=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick").doesNotExist())
                .andExpect(jsonPath("$.considered").value(0));
        tmdb.verify();
    }

    @Test
    void familySafeKeepsATitleRatedForEveryoneAndSaysSo() throws Exception {
        discoverPages(page(7, 11), "{\"results\":[]}");
        tmdbReturns("/movie/11?", "{\"id\":11,\"title\":\"Kids Film\",\"release_dates\":{\"results\":[{\"iso_3166_1\":\"IN\",\"release_dates\":[{\"certification\":\"U\"}]}]}}");
        tmdbReturns("/movie/11/watch/providers", TmdbFixtures.PROVIDERS);

        mvc.perform(get("/api/tonight?familySafe=true&seed=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick.id").value(11))
                .andExpect(jsonPath("$.details.certification").value("U"))
                .andExpect(jsonPath("$.details.familyFriendly").value(true))
                .andExpect(jsonPath("$.reasons", Matchers.hasItem("Suitable for all ages")));
    }

    @Test
    void theSameSeedRepeatsTheAnswerAndOtherSeedsVary() throws Exception {
        long first = pickFor(12345);
        org.junit.jupiter.api.Assertions.assertEquals(first, pickFor(12345));

        java.util.Set<Long> picks = new java.util.HashSet<>();
        for (long seed = 1; seed <= 12; seed++) {
            picks.add(pickFor(seed));
        }
        org.junit.jupiter.api.Assertions.assertTrue(picks.size() > 1, "different seeds should give different titles sometimes");
    }

    @Test
    void aSignedInUserDoesNotGetWatchedTitlesAndGetsWantedOnesFirst() throws Exception {
        String auth = register("asha");
        User asha = userRepository.findByUsername("asha").orElseThrow();
        WatchlistItem seen = new WatchlistItem(asha, 11, "movie", "T11", "/p11.jpg", 2024);
        seen.update(WatchStatus.WATCHED, null, null);
        watchlistRepository.save(seen);
        watchlistRepository.save(new WatchlistItem(asha, 12, "movie", "T12", "/p12.jpg", 2024)); // default status: want to watch

        discoverPages(page(7, 11, 12, 13), "{\"results\":[]}");
        tmdbReturns("/movie/12?", TmdbFixtures.MOVIE_DETAIL);
        tmdbReturns("/movie/12/watch/providers", TmdbFixtures.PROVIDERS);

        mvc.perform(get("/api/tonight?seed=1").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick.id").value(12))
                .andExpect(jsonPath("$.pick.onWatchlist").value(true))
                .andExpect(jsonPath("$.reasons[0]").value("On your watchlist"))
                .andExpect(jsonPath("$.considered").value(2))
                .andExpect(jsonPath("$.alternatives[*].id").value(Matchers.contains(13)));
        tmdb.verify();
    }

    @Test
    void aFailingDetailLookupStillGivesAnAnswerWithoutDetails() throws Exception {
        discoverPages(page(7, 11), "{\"results\":[]}");
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/11?"))).andRespond(withServerError());
        tmdbReturns("/movie/11/watch/providers", TmdbFixtures.PROVIDERS);

        mvc.perform(get("/api/tonight?seed=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick.id").value(11))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(jsonPath("$.providers.region").value("IN"));
    }

    @Test
    void nothingMatchingIsAnEmptyAnswerNotAnError() throws Exception {
        discoverPages("{\"results\":[]}", "{\"results\":[]}");

        mvc.perform(get("/api/tonight?mood=scary&seed=3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pick").doesNotExist())
                .andExpect(jsonPath("$.alternatives.length()").value(0))
                .andExpect(jsonPath("$.considered").value(0))
                .andExpect(jsonPath("$.seed").value(3));
    }

    @Test
    void invalidWishesAre400AndNeverReachTmdb() throws Exception {
        for (String url : new String[] { "/api/tonight?type=book", "/api/tonight?mood=zzz", "/api/tonight?maxMinutes=5",
                "/api/tonight?maxMinutes=9999", "/api/tonight?language=english", "/api/tonight?maxMinutes=abc", "/api/tonight?seed=x" }) {
            mvc.perform(get(url)).andExpect(status().isBadRequest());
        }
        tmdb.verify(); // no expectation was set: any TMDB call would have failed
    }

    @Test
    void aTmdbOutageOnTheFirstPageIs502() throws Exception {
        tmdb.expect(once(), requestTo(Matchers.containsString("/discover/movie"))).andRespond(withServerError());

        mvc.perform(get("/api/tonight?seed=1")).andExpect(status().isBadGateway());
    }
}
