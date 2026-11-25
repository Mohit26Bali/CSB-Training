package com.csb.cinescope.controller;

import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import com.csb.cinescope.AbstractApiTest;

/**
 * The episode-rating heatmap through the real stack with only TMDB replaced. The fake show has three seasons plus "Specials":
 * season 1 (episodes listed out of order) has 7.0, 9.6, 7.5 and 8.0; season 2 has 8.2, 4.2, one with no votes and one with a vote
 * average of 0; season 3 cannot be loaded. Season 0 must never be asked for.
 */
class EpisodeApiTest extends AbstractApiTest {

    private static final String SHOW = "{\"id\":77,\"name\":\"The Show\",\"seasons\":[{\"season_number\":0},{\"season_number\":1},{\"season_number\":2},{\"season_number\":3}]}";

    private static String episode(int number, String name, double average, int votes, String airDate) {
        return "{\"episode_number\":" + number + ",\"name\":\"" + name + "\",\"vote_average\":" + average + ",\"vote_count\":" + votes + ",\"air_date\":\"" + airDate + "\"}";
    }

    private static final String SEASON_1 = "{\"episodes\":[" + episode(3, "Three", 7.5, 120, "2020-01-15") + "," + episode(1, "Pilot", 7.0, 400, "2020-01-01") + ","
            + episode(4, "Four", 8.0, 110, "2020-01-22") + "," + episode(2, "The Turn", 9.6, 300, "2020-01-08") + "]}";
    private static final String SEASON_2 = "{\"episodes\":[" + episode(1, "Back", 8.2, 90, "2021-01-01") + "," + episode(2, "The Dud", 4.2, 150, "2021-01-08") + ","
            + episode(3, "Nobody Voted", 0, 0, "2021-01-15") + "," + episode(4, "Zero Average", 0, 5, "2021-01-22") + "," + episode(5, "Not Yet", 0, 0, "2999-01-01") + "]}";

    /** Answers any TMDB request by its path; {@code seasons} says what each season path returns. */
    private AtomicInteger specialsRequests = new AtomicInteger();

    private void tmdbHasTheShow(Function<String, org.springframework.test.web.client.ResponseCreator> seasons) {
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/3/"))).andRespond(request -> {
            String path = request.getURI().getPath();
            if (path.endsWith("/season/0")) {
                specialsRequests.incrementAndGet();
            }
            if (path.endsWith("/tv/77")) {
                return withSuccess(SHOW, MediaType.APPLICATION_JSON).createResponse(request);
            }
            return seasons.apply(path).createResponse(request);
        });
    }

    private void tmdbHasTheUsualSeasons() {
        tmdbHasTheShow(path -> path.endsWith("/season/1") ? withSuccess(SEASON_1, MediaType.APPLICATION_JSON)
                : path.endsWith("/season/2") ? withSuccess(SEASON_2, MediaType.APPLICATION_JSON)
                : withServerError());
    }

    @Test
    void everyEpisodeIsListedSeasonBySeasonInOrderWithTheNumbersAddedUp() throws Exception {
        tmdbHasTheUsualSeasons();

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("The Show"))
                .andExpect(jsonPath("$.seasonCount").value(3)) // the specials are not counted
                .andExpect(jsonPath("$.episodeCount").value(9))
                .andExpect(jsonPath("$.ratedCount").value(6))
                .andExpect(jsonPath("$.average").value(7.4)) // (7.0 + 9.6 + 7.5 + 8.0 + 8.2 + 4.2) / 6 = 7.416...
                .andExpect(jsonPath("$.seasons[0].number").value(1))
                .andExpect(jsonPath("$.seasons[0].episodes[*].name", Matchers.contains("Pilot", "The Turn", "Three", "Four"))) // sorted by episode number
                .andExpect(jsonPath("$.seasons[0].average").value(8.0)) // 8.0375
                .andExpect(jsonPath("$.seasons[1].average").value(6.2));
    }

    @Test
    void theBestAndWorstEpisodesAreNamed() throws Exception {
        tmdbHasTheUsualSeasons();

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(jsonPath("$.best.name").value("The Turn")).andExpect(jsonPath("$.best.season").value(1)).andExpect(jsonPath("$.best.number").value(2)).andExpect(jsonPath("$.best.rating").value(9.6))
                .andExpect(jsonPath("$.worst.name").value("The Dud")).andExpect(jsonPath("$.worst.season").value(2)).andExpect(jsonPath("$.worst.number").value(2)).andExpect(jsonPath("$.worst.rating").value(4.2));
    }

    @Test
    void anEpisodeNobodyHasRatedIsBlankNotZeroAndDoesNotDragTheAverageDown() throws Exception {
        tmdbHasTheUsualSeasons();

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(jsonPath("$.seasons[1].episodes[2].name").value("Nobody Voted")).andExpect(jsonPath("$.seasons[1].episodes[2].rating").doesNotExist())
                .andExpect(jsonPath("$.seasons[1].episodes[3].name").value("Zero Average")).andExpect(jsonPath("$.seasons[1].episodes[3].rating").doesNotExist()) // votes but an average of 0: not a real score
                .andExpect(jsonPath("$.seasons[1].episodes[2].aired").value(true))
                .andExpect(jsonPath("$.seasons[1].episodes[4].name").value("Not Yet")).andExpect(jsonPath("$.seasons[1].episodes[4].aired").value(false));
    }

    @Test
    void aSeasonTmdbCannotSupplyIsMarkedUnavailableAndTheRestStillShow() throws Exception {
        tmdbHasTheUsualSeasons();

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seasons[2].number").value(3))
                .andExpect(jsonPath("$.seasons[2].unavailable").value(true))
                .andExpect(jsonPath("$.seasons[2].episodes.length()").value(0))
                .andExpect(jsonPath("$.seasons[0].unavailable").value(false));
    }

    @Test
    void theSpecialsSeasonIsNeverAskedFor() throws Exception {
        tmdbHasTheUsualSeasons();

        mvc.perform(get("/api/titles/tv/77/episodes")).andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertEquals(0, specialsRequests.get());
    }

    @Test
    void withFewerThanTwoRatedEpisodesThereIsNothingToCompare() throws Exception {
        tmdbHasTheShow(path -> path.endsWith("/season/1")
                ? withSuccess("{\"episodes\":[" + episode(1, "Only", 8.8, 40, "2020-01-01") + "," + episode(2, "Unrated", 0, 0, "2020-01-08") + "]}", MediaType.APPLICATION_JSON)
                : withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(jsonPath("$.ratedCount").value(1))
                .andExpect(jsonPath("$.average").value(8.8))
                .andExpect(jsonPath("$.best").doesNotExist())
                .andExpect(jsonPath("$.worst").doesNotExist());
    }

    @Test
    void whenEveryEpisodeHasTheSameScoreOneEpisodeIsNeverBothBestAndWorst() throws Exception {
        tmdbHasTheShow(path -> path.endsWith("/season/1")
                ? withSuccess("{\"episodes\":[" + episode(1, "A", 8.0, 10, "2020-01-01") + "," + episode(2, "B", 8.0, 50, "2020-01-08") + "]}", MediaType.APPLICATION_JSON)
                : withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(jsonPath("$.best.name").value("B")) // more people rated it
                .andExpect(jsonPath("$.worst.name").value("A"));
    }

    @Test
    void aShowWithNoRatingsAtAllHasAnEmptyHeatmapNotAnError() throws Exception {
        tmdbHasTheShow(path -> withSuccess("{\"episodes\":[" + episode(1, "Fresh", 0, 0, "2999-01-01") + "]}", MediaType.APPLICATION_JSON));

        mvc.perform(get("/api/titles/tv/77/episodes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ratedCount").value(0))
                .andExpect(jsonPath("$.average").doesNotExist())
                .andExpect(jsonPath("$.best").doesNotExist());
    }

    @Test
    void anUnknownShowIsNotFound() throws Exception {
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/tv/404"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(get("/api/titles/tv/404/episodes")).andExpect(status().isNotFound());
    }

    @Test
    void theSecondRequestIsAnsweredFromTheCache() throws Exception {
        // every season loads here: a season that failed is deliberately not cached, so that it is tried again next time
        tmdbHasTheShow(path -> withSuccess(path.endsWith("/season/1") ? SEASON_1 : SEASON_2, MediaType.APPLICATION_JSON));
        mvc.perform(get("/api/titles/tv/77/episodes")).andExpect(status().isOk());

        tmdb.reset(); // from now on any TMDB request fails the test
        mvc.perform(get("/api/titles/tv/77/episodes")).andExpect(status().isOk()).andExpect(jsonPath("$.seasonCount").value(3));
    }

    @Test
    void theHeatmapNeedsALogin() throws Exception {
        anonymous().perform(get("/api/titles/tv/77/episodes")).andExpect(status().isUnauthorized());
    }
}
