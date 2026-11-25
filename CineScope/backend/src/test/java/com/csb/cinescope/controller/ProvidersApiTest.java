package com.csb.cinescope.controller;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.TmdbFixtures;

/**
 * "Where to watch" through the real stack (controller, service, mapper, caching gateway) with only TMDB replaced:
 * India by default, another country on request, caching across countries, validation and TMDB failures. Public: no login.
 */
class ProvidersApiTest extends AbstractApiTest {

    private static final String PATH = "/movie/550/watch/providers";

    @Test
    void defaultsToIndiaAndGroupsTheServices() throws Exception {
        tmdbReturns(PATH, TmdbFixtures.PROVIDERS);

        mvc.perform(get("/api/titles/movie/550/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("IN"))
                .andExpect(jsonPath("$.stream[0].name").value("Netflix"))
                .andExpect(jsonPath("$.stream[0].logoPath").value("/nf.jpg"))
                .andExpect(jsonPath("$.stream.length()").value(2))
                .andExpect(jsonPath("$.free.length()").value(2))
                .andExpect(jsonPath("$.rent[0].name").value("Apple TV"))
                .andExpect(jsonPath("$.buy[0].name").value("Google Play Movies"))
                .andExpect(jsonPath("$.link").value(Matchers.containsString("locale=IN")));
        tmdb.verify();
    }

    @Test
    void anotherCountryCanBeAskedForInAnyLetterCaseAndSharesOneTmdbCall() throws Exception {
        tmdbReturns(PATH, TmdbFixtures.PROVIDERS); // exactly one request is expected

        mvc.perform(get("/api/titles/movie/550/providers?region=us"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.region").value("US"))
                .andExpect(jsonPath("$.stream.length()").value(0))
                .andExpect(jsonPath("$.buy[0].name").value("Apple TV"));
        mvc.perform(get("/api/titles/movie/550/providers?region=IN")) // answered from the cache: TMDB holds all countries
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stream[0].name").value("Netflix"));
        tmdb.verify();
    }

    @Test
    void aCountryWithNothingGivesEmptyGroupsNotAnError() throws Exception {
        tmdbReturns("/tv/1399/watch/providers", "{\"id\":1399,\"results\":{}}");

        mvc.perform(get("/api/titles/tv/1399/providers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.link").doesNotExist())
                .andExpect(jsonPath("$.stream.length()").value(0))
                .andExpect(jsonPath("$.rent.length()").value(0));
    }

    @Test
    void badInputIsRejectedBeforeTmdbIsCalled() throws Exception {
        mvc.perform(get("/api/titles/book/1/providers")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/titles/movie/0/providers")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/titles/movie/550/providers?region=India")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/titles/movie/550/providers?region=1N")).andExpect(status().isBadRequest());
        tmdb.verify(); // no expectation was set, so any TMDB call would have failed the test
    }

    @Test
    void anUnknownTitleIs404AndATmdbOutageIs502() throws Exception {
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/9/watch/providers"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/8/watch/providers"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mvc.perform(get("/api/titles/movie/9/providers")).andExpect(status().isNotFound());
        mvc.perform(get("/api/titles/movie/8/providers")).andExpect(status().isBadGateway());
    }
}
