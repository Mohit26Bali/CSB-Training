package com.csb.cinescope.controller;

import static org.springframework.test.web.client.ExpectedCount.manyTimes;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.stream.Collectors;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.TmdbFixtures;
import com.csb.cinescope.repository.QuoteRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The titles-and-famous-lines endpoint behind the sign-in screens, through the real stack with only TMDB replaced. It is the
 * one catalogue endpoint open to visitors, so every request here carries no token. The quotes come from the seeded
 * {@code quotes} table and are chosen at random, so these tests check properties (how many, which ones, never a repeat) rather
 * than a fixed list.
 */
class ShowcaseApiTest extends AbstractApiTest {

    private static final String NO_BACKDROP = "{\"id\":11,\"title\":\"Plain\",\"release_date\":\"1977-05-25\",\"poster_path\":\"/p.jpg\"}";
    private static final String TV_DETAIL = "{\"id\":1396,\"name\":\"A Show\",\"first_air_date\":\"2008-01-20\",\"poster_path\":\"/sp.jpg\",\"backdrop_path\":\"/sb.jpg\"}";

    @Autowired QuoteRepository quotes;
    private final ObjectMapper json = new ObjectMapper();

    /** Answers any TMDB title request: shows with a show, films with the shared film fixture. */
    private void tmdbHasEveryTitle() {
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/3/"))).andRespond(request ->
                withSuccess(request.getURI().getPath().contains("/tv/") ? TV_DETAIL : TmdbFixtures.MOVIE_DETAIL, MediaType.APPLICATION_JSON).createResponse(request));
    }

    private List<Long> quoteIds(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString()).findValues("quoteId").stream().map(n -> n.asLong()).collect(Collectors.toList());
    }

    @Test
    void visitorsGetTwelveSlidesWithAFamousLineEach() throws Exception {
        tmdbHasEveryTitle();

        anonymous().perform(get("/api/showcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[*].quote", Matchers.everyItem(Matchers.not(Matchers.emptyString()))))
                .andExpect(jsonPath("$[*].speaker", Matchers.everyItem(Matchers.not(Matchers.emptyString()))))
                .andExpect(jsonPath("$[*].backdropPath", Matchers.everyItem(Matchers.notNullValue())))
                .andExpect(jsonPath("$[*].quoteId", Matchers.hasSize(12)))
                .andExpect(jsonPath("$[*].category", Matchers.everyItem(Matchers.isIn(List.of("HOLLYWOOD", "TV", "BOLLYWOOD", "ANIME")))))
                .andExpect(jsonPath("$[*].type", Matchers.everyItem(Matchers.isIn(List.of("movie", "tv")))));
    }

    @Test
    void theSlidesMixTheCategoriesInsteadOfShowingOnlyTheBiggest() throws Exception {
        tmdbHasEveryTitle();

        MvcResult result = anonymous().perform(get("/api/showcase")).andExpect(status().isOk()).andReturn();
        var categories = json.readTree(result.getResponse().getContentAsString()).findValues("category").stream().map(n -> n.asText()).collect(Collectors.toSet());
        org.assertj.core.api.Assertions.assertThat(categories).containsExactlyInAnyOrder("HOLLYWOOD", "TV", "BOLLYWOOD", "ANIME");
    }

    @Test
    void quotesTheBrowserAlreadyShowedAreNotSentAgain() throws Exception {
        tmdbHasEveryTitle();

        List<Long> first = quoteIds(anonymous().perform(get("/api/showcase")).andReturn());
        String shown = first.stream().map(String::valueOf).collect(Collectors.joining(","));
        List<Long> second = quoteIds(anonymous().perform(get("/api/showcase").param("exclude", shown)).andExpect(status().isOk()).andReturn());

        org.assertj.core.api.Assertions.assertThat(second).hasSize(12).doesNotContainAnyElementsOf(first);
    }

    @Test
    void whenTheBrowserHasSeenEverythingItStillGetsSlidesInsteadOfNothing() throws Exception {
        tmdbHasEveryTitle();
        String everything = quotes.findAll().stream().map(q -> String.valueOf(q.getId())).collect(Collectors.joining(","));

        anonymous().perform(get("/api/showcase").param("exclude", everything))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12));
    }

    @Test
    void aTitleThatCannotBeLoadedOrHasNoBackdropIsLeftOutAndTheRestStillShow() throws Exception {
        // every film fails or has no backdrop except shows: only the shows can appear
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/3/"))).andRespond(request -> {
            String path = request.getURI().getPath();
            if (path.contains("/tv/")) {
                return withSuccess(TV_DETAIL, MediaType.APPLICATION_JSON).createResponse(request);
            }
            return path.hashCode() % 2 == 0
                    ? withServerError().createResponse(request)
                    : withSuccess(NO_BACKDROP, MediaType.APPLICATION_JSON).createResponse(request);
        });

        anonymous().perform(get("/api/showcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", Matchers.everyItem(Matchers.is("tv"))))
                .andExpect(jsonPath("$[*].title", Matchers.everyItem(Matchers.is("A Show"))));
    }

    @Test
    void ifTmdbIsDownTheAnswerIsAnEmptyListNotAnError() throws Exception {
        tmdb.expect(manyTimes(), requestTo(Matchers.containsString("/3/"))).andRespond(withServerError());

        anonymous().perform(get("/api/showcase"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void anOversizedExcludeListIsCutInsteadOfRefused() throws Exception {
        tmdbHasEveryTitle();
        String huge = java.util.stream.LongStream.rangeClosed(1, 5000).mapToObj(String::valueOf).collect(Collectors.joining(","));

        anonymous().perform(get("/api/showcase").param("exclude", huge)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(12));
    }
}
