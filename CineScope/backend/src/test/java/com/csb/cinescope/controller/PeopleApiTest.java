package com.csb.cinescope.controller;

import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.FakeTmdbConfig;
import com.csb.cinescope.TmdbFixtures;

/**
 * Person pages through the real stack with only TMDB replaced: the JSON a browser receives, the request sent to TMDB
 * (credits appended in the same call), caching, validation, failures, and that no login is needed.
 */
class PeopleApiTest extends AbstractApiTest {

    @Test
    void returnsTheProfileAndFilmographyWithoutALoginAndAsksTmdbForCreditsInTheSameCall() throws Exception {
        tmdb.expect(once(), requestTo(Matchers.containsString("/person/3001")))
                .andExpect(queryParam("append_to_response", "combined_credits"))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(TmdbFixtures.PERSON, org.springframework.http.MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/people/3001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mara Whitfield"))
                .andExpect(jsonPath("$.birthday").value("1975-04-12"))
                .andExpect(jsonPath("$.knownFor[0].title").value("Long Show"))
                .andExpect(jsonPath("$.acting.length()").value(6))
                .andExpect(jsonPath("$.acting[0].role").value("Eve"))
                .andExpect(jsonPath("$.crew[0].role").value("Director, Writer"))
                .andExpect(jsonPath("$.crew[0].posterPath").value("/new.jpg"));
        tmdb.verify();
    }

    @Test
    void theSecondRequestForTheSamePersonIsServedFromTheCache() throws Exception {
        tmdbReturns("/person/3001", TmdbFixtures.PERSON); // exactly one TMDB request is expected

        mvc.perform(get("/api/people/3001")).andExpect(status().isOk());
        mvc.perform(get("/api/people/3001")).andExpect(status().isOk());
        tmdb.verify();
    }

    @Test
    void badIdsAreRejectedBeforeTmdbIsCalled() throws Exception {
        mvc.perform(get("/api/people/0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/people/-5")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/people/abc")).andExpect(status().isBadRequest());
        tmdb.verify();
    }

    @Test
    void anUnknownPersonIs404AndATmdbOutageIs502AndTheTokenNeverLeaks() throws Exception {
        tmdb.expect(once(), requestTo(Matchers.containsString("/person/9"))).andRespond(withStatus(HttpStatus.NOT_FOUND));
        tmdb.expect(once(), requestTo(Matchers.containsString("/person/8"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        mvc.perform(get("/api/people/9")).andExpect(status().isNotFound());
        mvc.perform(get("/api/people/8"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(Matchers.not(Matchers.containsString(FakeTmdbConfig.TOKEN))));
    }
}
