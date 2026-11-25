package com.csb.cinescope.controller;

import static com.csb.cinescope.TmdbFixtures.GENRES_MOVIE;
import static com.csb.cinescope.TmdbFixtures.MOVIE_DETAIL;
import static com.csb.cinescope.TmdbFixtures.POPULAR_MOVIES;
import static com.csb.cinescope.TmdbFixtures.SEARCH_MULTI;
import static com.csb.cinescope.TmdbFixtures.TRENDING_ALL;
import static com.csb.cinescope.TmdbFixtures.TV_DETAIL;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.never;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.FakeTmdbConfig;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.response.MockRestResponseCreators;

/**
 * End-to-end API tests of the catalogue: real controller, real service, real mapper, real caching gateway, with only
 * TMDB itself replaced by a mock HTTP server holding canned answers (see {@code TmdbFixtures}). They check the JSON a
 * browser would receive, the exact requests sent to TMDB (token, parameters), caching, and the error status codes.
 * The test token is {@value #TOKEN}; one test asserts that it never appears in any response.
 */
class CatalogApiTest extends AbstractApiTest {

    static final String TOKEN = FakeTmdbConfig.TOKEN;

    // ---- happy paths ----

    @Test
    void titleDetailReturnsOurOwnShapeAndSendsTheSecretTokenToTmdb() throws Exception {
        tmdbReturns("/movie/550", MOVIE_DETAIL);

        mvc.perform(get("/api/titles/movie/550"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Fight Club"))
                .andExpect(jsonPath("$.year").value(1999))
                .andExpect(jsonPath("$.certification").value("A"))
                .andExpect(jsonPath("$.familyFriendly").value(false))
                .andExpect(jsonPath("$.directors[0].name").value("David Fincher"))
                .andExpect(jsonPath("$.cast.length()").value(2))
                .andExpect(jsonPath("$.videos[0].key").value("trailer1"))
                .andExpect(jsonPath("$.similar[0].title").value("Pulp Fiction"))
                .andExpect(jsonPath("$.posterPath").value("/fc.jpg"));
        tmdb.verify();
    }

    @Test
    void tvDetailWorksToo() throws Exception {
        tmdbReturns("/tv/1396", TV_DETAIL);

        mvc.perform(get("/api/titles/tv/1396"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Breaking Bad"))
                .andExpect(jsonPath("$.seasons").value(5))
                .andExpect(jsonPath("$.certification").value("TV-MA"));
    }

    @Test
    void theSecretTokenNeverAppearsInAResponse() throws Exception {
        tmdbReturns("/movie/550", MOVIE_DETAIL);

        mvc.perform(get("/api/titles/movie/550")).andExpect(content().string(not(containsString(TOKEN))));
    }

    @Test
    void aListReturnsPagedTitles() throws Exception {
        tmdbReturns("/movie/popular", POPULAR_MOVIES);

        mvc.perform(get("/api/titles?category=popular&type=movie&page=1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].title").value("Alpha"))
                .andExpect(jsonPath("$.items[0].type").value("movie"))
                .andExpect(jsonPath("$.totalPages").value(500));
    }

    @Test
    void searchFindsMoviesShowsAndPeople() throws Exception {
        tmdbReturns("/search/multi", SEARCH_MULTI);

        mvc.perform(get("/api/search?query=nolan"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.items[0].type").value("person"))
                .andExpect(jsonPath("$.items[1].title").value("Inception"));
    }

    @Test
    void discoverSendsTheFiltersToTmdb() throws Exception {
        tmdb.expect(once(), requestTo(containsString("/discover/movie")))
                .andExpect(queryParam("with_genres", "28"))
                .andExpect(queryParam("primary_release_year", "2023"))
                .andExpect(queryParam("sort_by", "vote_average.desc"))
                .andRespond(withSuccess(POPULAR_MOVIES, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/discover?type=movie&genre=28&year=2023&sort=rating"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2));
        tmdb.verify();
    }

    @Test
    void genresAreListed() throws Exception {
        tmdbReturns("/genre/movie/list", GENRES_MOVIE);

        mvc.perform(get("/api/genres?type=movie"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Action"));
    }

    @Test
    void theHomePageCombinesAllRowsInOneResponse() throws Exception {
        tmdb.expect(org.springframework.test.web.client.ExpectedCount.times(8), requestTo(containsString("tmdb.test")))
                .andRespond(withSuccess(TRENDING_ALL, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/home"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hero.title").value("The Last Lighthouse"))
                .andExpect(jsonPath("$.heroes[0].title").value("The Last Lighthouse"))
                .andExpect(jsonPath("$.heroes.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.rows.length()").value(8))
                .andExpect(jsonPath("$.rows[0].key").value("trending-today"))
                .andExpect(jsonPath("$.rows[0].title").value("Trending today"))
                .andExpect(jsonPath("$.rows[1].key").value("trending"))
                .andExpect(jsonPath("$.rows[2].title").value("Popular movies"));
        tmdb.verify();
    }

    // ---- caching ----

    @Test
    void theSameRequestIsServedFromTheCacheTheSecondTime() throws Exception {
        tmdbReturns("/movie/550", MOVIE_DETAIL); // expected exactly ONCE

        mvc.perform(get("/api/titles/movie/550")).andExpect(status().isOk());
        mvc.perform(get("/api/titles/movie/550")).andExpect(status().isOk());

        tmdb.verify(); // a second upstream call would have failed the "once" expectation
    }

    @Test
    void differentParametersAreCachedSeparately() throws Exception {
        tmdb.expect(once(), requestTo(containsString("page=1"))).andRespond(withSuccess(POPULAR_MOVIES, MediaType.APPLICATION_JSON));
        tmdb.expect(once(), requestTo(containsString("page=2"))).andRespond(withSuccess(POPULAR_MOVIES, MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/titles?category=popular&type=movie&page=1")).andExpect(status().isOk());
        mvc.perform(get("/api/titles?category=popular&type=movie&page=2")).andExpect(status().isOk());

        tmdb.verify();
    }

    @Test
    void failuresAreNotCached() throws Exception {
        tmdb.expect(once(), requestTo(containsString("/movie/550"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        tmdbReturns("/movie/550", MOVIE_DETAIL);

        mvc.perform(get("/api/titles/movie/550")).andExpect(status().isBadGateway());
        mvc.perform(get("/api/titles/movie/550")).andExpect(status().isOk()); // retried, and this time it works

        tmdb.verify();
    }

    // ---- errors ----

    @Test
    void anUnknownTitleIs404() throws Exception {
        tmdb.expect(once(), requestTo(containsString("/movie/999"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/titles/movie/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Not found"));
    }

    @Test
    void tmdbRateLimitingIs503_andATimeoutIs504() throws Exception {
        tmdb.expect(once(), requestTo(containsString("/movie/1"))).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        tmdb.expect(once(), requestTo(containsString("/movie/2"))).andRespond(MockRestResponseCreators.withException(new java.net.SocketTimeoutException("Read timed out")));

        mvc.perform(get("/api/titles/movie/1")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/titles/movie/2")).andExpect(status().isGatewayTimeout());
    }

    @Test
    void invalidRequestsAre400AndNeverReachTmdb() throws Exception {
        tmdb.expect(never(), requestTo(containsString("tmdb.test")));

        mvc.perform(get("/api/titles/book/1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/titles?category=bogus")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/titles?page=0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/titles?page=abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/search")).andExpect(status().isBadRequest()); // missing parameter
        mvc.perform(get("/api/search").param("query", "  ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/discover?sort=zzz")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/genres?type=anime")).andExpect(status().isBadRequest());

        tmdb.verify();
    }

    @Test
    void errorsHaveOneConsistentShape() throws Exception {
        mvc.perform(get("/api/titles/book/1"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isNotEmpty())
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
        // Deny by default: an anonymous caller probing an unknown path is asked to log in; a logged-in user gets the 404.
        anonymous().perform(get("/api/nothing-here")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/nothing-here").header("Authorization", register("asha")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("No such endpoint"));
    }

    // ---- documentation ----

    @Test
    void swaggerAndTheOpenApiDocumentAreServed() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("CineScope API"))
                .andExpect(jsonPath("$.paths['/api/home']").exists())
                .andExpect(jsonPath("$.paths['/api/titles/{type}/{id}']").exists())
                .andExpect(jsonPath("$.paths['/api/search']").exists());
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }
}
