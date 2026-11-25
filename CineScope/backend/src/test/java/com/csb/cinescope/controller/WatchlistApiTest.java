package com.csb.cinescope.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.TmdbFixtures;

/**
 * The watchlist through the real stack (security, controller, service, H2 database) with TMDB replaced by a mock: adding and
 * changing titles, the rating rule, listing with filters / sorting / paging, counts, removal, and above all that users can
 * never see or touch each other's lists.
 */
class WatchlistApiTest extends AbstractApiTest {

    private static String detail(long id, String title, String date) {
        return "{\"id\":%d,\"title\":\"%s\",\"poster_path\":\"/p%d.jpg\",\"release_date\":\"%s\"}".formatted(id, title, id, date);
    }

    private static String tvDetail(long id, String name, String date) {
        return "{\"id\":%d,\"name\":\"%s\",\"poster_path\":\"/p%d.jpg\",\"first_air_date\":\"%s\"}".formatted(id, name, id, date);
    }

    private ResultActions putAs(String token, String path, String json) throws Exception {
        return mvc.perform(put(path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions getAs(String token, String path) throws Exception {
        return mvc.perform(get(path).header("Authorization", token));
    }

    // ---- security ----

    @Test
    void everyWatchlistEndpointNeedsALogin() throws Exception {
        MockMvc visitor = anonymous();
        visitor.perform(get("/api/watchlist")).andExpect(status().isUnauthorized());
        visitor.perform(get("/api/watchlist/summary")).andExpect(status().isUnauthorized());
        visitor.perform(get("/api/watchlist/movie/550")).andExpect(status().isUnauthorized());
        visitor.perform(put("/api/watchlist/movie/550").contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"WANT\"}")).andExpect(status().isUnauthorized());
        visitor.perform(delete("/api/watchlist/movie/550")).andExpect(status().isUnauthorized());
    }

    // ---- adding and changing ----

    @Test
    void addingATitleStoresASnapshotFromTmdb() throws Exception {
        String ravi = register("ravi");
        tmdbReturns("/movie/550", TmdbFixtures.MOVIE_DETAIL);

        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tmdbId").value(550))
                .andExpect(jsonPath("$.type").value("movie"))
                .andExpect(jsonPath("$.title").value("Fight Club"))
                .andExpect(jsonPath("$.posterPath").value("/fc.jpg"))
                .andExpect(jsonPath("$.year").value(1999))
                .andExpect(jsonPath("$.status").value("WANT"))
                .andExpect(jsonPath("$.rating").doesNotExist())
                .andExpect(jsonPath("$.addedAt").isNotEmpty());
        tmdb.verify();
        assertEquals(1, watchlistRepository.count());
    }

    @Test
    void changingAnExistingEntryAnswers200AndDoesNotCallTmdbAgain() throws Exception {
        String ravi = register("ravi");
        tmdbReturns("/movie/550", TmdbFixtures.MOVIE_DETAIL); // expected exactly once

        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\"}").andExpect(status().isCreated());
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WATCHED\",\"rating\":9,\"note\":\"  Tyler is right.  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WATCHED"))
                .andExpect(jsonPath("$.rating").value(9))
                .andExpect(jsonPath("$.note").value("Tyler is right."));

        tmdb.verify();
        assertEquals(1, watchlistRepository.count());
        getAs(ravi, "/api/watchlist/movie/550").andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(9));
    }

    @Test
    void movingBackFromWatchedClearsTheRating() throws Exception {
        String ravi = register("ravi");
        tmdbReturns("/movie/550", TmdbFixtures.MOVIE_DETAIL);
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WATCHED\",\"rating\":8}").andExpect(status().isCreated());

        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WATCHING\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating").doesNotExist());
    }

    @Test
    void aRatingNeedsStatusWatchedAndStaysBetween1And10() throws Exception {
        String ravi = register("ravi");

        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\",\"rating\":8}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A rating can only be given to a title you have watched"));
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WATCHED\",\"rating\":11}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").value("rating must be between 1 and 10"));
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WATCHED\",\"rating\":0}").andExpect(status().isBadRequest());
        assertEquals(0, watchlistRepository.count());
    }

    @Test
    void invalidBodiesAreRejected() throws Exception {
        String ravi = register("ravi");

        putAs(ravi, "/api/watchlist/movie/550", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"SOMEDAY\"}").andExpect(status().isBadRequest());
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\",\"note\":\"" + "x".repeat(501) + "\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.note").exists());
        putAs(ravi, "/api/watchlist/book/550", "{\"status\":\"WANT\"}").andExpect(status().isBadRequest());
        putAs(ravi, "/api/watchlist/movie/0", "{\"status\":\"WANT\"}").andExpect(status().isBadRequest());
        putAs(ravi, "/api/watchlist/movie/abc", "{\"status\":\"WANT\"}").andExpect(status().isBadRequest());
    }

    @Test
    void aTitleThatDoesNotExistAtTmdbIs404AndNothingIsStored() throws Exception {
        String ravi = register("ravi");
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/999"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        putAs(ravi, "/api/watchlist/movie/999", "{\"status\":\"WANT\"}").andExpect(status().isNotFound());
        assertEquals(0, watchlistRepository.count());
    }

    @Test
    void whenTmdbIsDownAddingFailsCleanlyWithA502() throws Exception {
        String ravi = register("ravi");
        tmdb.expect(once(), requestTo(Matchers.containsString("/movie/550"))).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\"}").andExpect(status().isBadGateway());
        assertEquals(0, watchlistRepository.count());
    }

    @Test
    void aMovieAndAShowWithTheSameTmdbIdAreSeparateEntries() throws Exception {
        String ravi = register("ravi");
        tmdbReturns("/movie/1", detail(1, "A Movie", "2001-01-01"));
        tmdbReturns("/tv/1", tvDetail(1, "A Show", "2002-02-02"));

        putAs(ravi, "/api/watchlist/movie/1", "{\"status\":\"WANT\"}").andExpect(status().isCreated());
        putAs(ravi, "/api/watchlist/tv/1", "{\"status\":\"WATCHING\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("A Show"))
                .andExpect(jsonPath("$.year").value(2002));

        assertEquals(2, watchlistRepository.count());
    }

    // ---- reading and removing ----

    @Test
    void aTitleNotOnTheListIs404_andRemovalWorksOnce() throws Exception {
        String ravi = register("ravi");
        tmdbReturns("/movie/550", TmdbFixtures.MOVIE_DETAIL);
        getAs(ravi, "/api/watchlist/movie/550").andExpect(status().isNotFound());
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\"}").andExpect(status().isCreated());

        mvc.perform(delete("/api/watchlist/movie/550").header("Authorization", ravi)).andExpect(status().isNoContent());
        getAs(ravi, "/api/watchlist/movie/550").andExpect(status().isNotFound());
        mvc.perform(delete("/api/watchlist/movie/550").header("Authorization", ravi)).andExpect(status().isNotFound());
    }

    // ---- isolation between users ----

    @Test
    void usersNeverSeeOrTouchEachOthersEntries() throws Exception {
        String ravi = register("ravi");
        String priya = register("priya");
        tmdbReturns("/movie/550", TmdbFixtures.MOVIE_DETAIL);
        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WATCHED\",\"rating\":10,\"note\":\"private\"}").andExpect(status().isCreated());

        getAs(priya, "/api/watchlist/movie/550").andExpect(status().isNotFound());
        getAs(priya, "/api/watchlist").andExpect(jsonPath("$.totalResults").value(0));
        getAs(priya, "/api/watchlist/summary").andExpect(jsonPath("$.total").value(0));
        mvc.perform(delete("/api/watchlist/movie/550").header("Authorization", priya)).andExpect(status().isNotFound());

        // ravi's entry is untouched by all of that
        getAs(ravi, "/api/watchlist/movie/550").andExpect(jsonPath("$.note").value("private")).andExpect(jsonPath("$.rating").value(10));
        assertEquals(1, watchlistRepository.count());
    }

    @Test
    void eachUserCanHaveTheSameTitleOnTheirOwnList() throws Exception {
        String ravi = register("ravi");
        String priya = register("priya");
        tmdb.expect(org.springframework.test.web.client.ExpectedCount.times(2), requestTo(Matchers.containsString("/movie/550")))
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(TmdbFixtures.MOVIE_DETAIL, MediaType.APPLICATION_JSON));

        putAs(ravi, "/api/watchlist/movie/550", "{\"status\":\"WANT\"}").andExpect(status().isCreated());
        putAs(priya, "/api/watchlist/movie/550", "{\"status\":\"WATCHED\"}").andExpect(status().isCreated());

        getAs(ravi, "/api/watchlist/movie/550").andExpect(jsonPath("$.status").value("WANT"));
        getAs(priya, "/api/watchlist/movie/550").andExpect(jsonPath("$.status").value("WATCHED"));
    }

    // ---- listing ----

    private void addThree(String token) throws Exception {
        tmdbReturns("/movie/1", detail(1, "Zebra Movie", "2001-01-01"));
        tmdbReturns("/movie/2", detail(2, "apple Movie", "2002-02-02"));
        tmdbReturns("/tv/3", tvDetail(3, "Mango Show", "2003-03-03"));
        putAs(token, "/api/watchlist/movie/1", "{\"status\":\"WANT\"}").andExpect(status().isCreated());
        putAs(token, "/api/watchlist/movie/2", "{\"status\":\"WATCHED\",\"rating\":7}").andExpect(status().isCreated());
        putAs(token, "/api/watchlist/tv/3", "{\"status\":\"WATCHING\"}").andExpect(status().isCreated());
    }

    @Test
    void listsNewestFirstByDefaultAndCountsPerStatus() throws Exception {
        String ravi = register("ravi");
        addThree(ravi);

        getAs(ravi, "/api/watchlist")
                .andExpect(jsonPath("$.totalResults").value(3))
                .andExpect(jsonPath("$.items[0].title").value("Mango Show")) // added last
                .andExpect(jsonPath("$.items[2].title").value("Zebra Movie"));
        getAs(ravi, "/api/watchlist/summary")
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.want").value(1))
                .andExpect(jsonPath("$.watching").value(1))
                .andExpect(jsonPath("$.watched").value(1));
    }

    @Test
    void filtersByStatusAndType() throws Exception {
        String ravi = register("ravi");
        addThree(ravi);

        getAs(ravi, "/api/watchlist?status=WATCHED").andExpect(jsonPath("$.totalResults").value(1)).andExpect(jsonPath("$.items[0].title").value("apple Movie"));
        getAs(ravi, "/api/watchlist?type=tv").andExpect(jsonPath("$.totalResults").value(1)).andExpect(jsonPath("$.items[0].title").value("Mango Show"));
        getAs(ravi, "/api/watchlist?type=movie&status=WANT").andExpect(jsonPath("$.totalResults").value(1)).andExpect(jsonPath("$.items[0].title").value("Zebra Movie"));
        getAs(ravi, "/api/watchlist?type=tv&status=WATCHED").andExpect(jsonPath("$.totalResults").value(0)).andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void sortsByTitleIgnoringCaseAndByRating() throws Exception {
        String ravi = register("ravi");
        addThree(ravi);

        getAs(ravi, "/api/watchlist?sort=title")
                .andExpect(jsonPath("$.items[0].title").value("apple Movie"))
                .andExpect(jsonPath("$.items[1].title").value("Mango Show"))
                .andExpect(jsonPath("$.items[2].title").value("Zebra Movie"));
        getAs(ravi, "/api/watchlist?sort=rating")
                .andExpect(jsonPath("$.items[0].title").value("apple Movie")); // the only rated one comes first; unrated last
    }

    @Test
    void pagesThroughTheList() throws Exception {
        String ravi = register("ravi");
        addThree(ravi);

        getAs(ravi, "/api/watchlist?size=2&page=1").andExpect(jsonPath("$.items.length()").value(2)).andExpect(jsonPath("$.totalPages").value(2));
        getAs(ravi, "/api/watchlist?size=2&page=2").andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].title").value("Zebra Movie"));
        getAs(ravi, "/api/watchlist?size=2&page=9").andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void invalidListParametersAre400() throws Exception {
        String ravi = register("ravi");

        getAs(ravi, "/api/watchlist?status=SOMEDAY").andExpect(status().isBadRequest());
        getAs(ravi, "/api/watchlist?type=book").andExpect(status().isBadRequest());
        getAs(ravi, "/api/watchlist?sort=zzz").andExpect(status().isBadRequest());
        getAs(ravi, "/api/watchlist?page=0").andExpect(status().isBadRequest());
        getAs(ravi, "/api/watchlist?size=0").andExpect(status().isBadRequest());
        getAs(ravi, "/api/watchlist?size=1000").andExpect(status().isBadRequest());
    }
}
