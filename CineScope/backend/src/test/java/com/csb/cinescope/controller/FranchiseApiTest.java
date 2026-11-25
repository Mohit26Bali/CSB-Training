package com.csb.cinescope.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.csb.cinescope.AbstractApiTest;
import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;

/**
 * The franchise tracker through the real stack with only TMDB replaced: the series a film belongs to, the person's progress through
 * it, and "add the rest to my watchlist". The fake series has five entries listed out of order: three released films, one not out
 * yet (2999) and one with no date at all.
 */
class FranchiseApiTest extends AbstractApiTest {

    private static final String FILM = "{\"id\":550,\"title\":\"Hero II\",\"belongs_to_collection\":{\"id\":9001,\"name\":\"Hero Collection\"}}";
    private static final String SOLO_FILM = "{\"id\":551,\"title\":\"Solo\",\"belongs_to_collection\":null}";
    private static final String COLLECTION = "{\"id\":9001,\"name\":\"Hero Collection\",\"poster_path\":\"/c.jpg\",\"parts\":["
            + "{\"id\":103,\"title\":\"Hero III\",\"release_date\":\"2008-07-01\",\"poster_path\":\"/p3.jpg\"},"
            + "{\"id\":101,\"title\":\"Hero\",\"release_date\":\"2001-05-01\",\"poster_path\":\"/p1.jpg\"},"
            + "{\"id\":105,\"title\":\"Hero Untitled\",\"release_date\":\"\",\"poster_path\":null},"
            + "{\"id\":102,\"title\":\"Hero II\",\"release_date\":\"2004-06-01\",\"poster_path\":\"/p2.jpg\"},"
            + "{\"id\":104,\"title\":\"Hero IV\",\"release_date\":\"2999-01-01\",\"poster_path\":\"/p4.jpg\"}]}";

    @Override
    protected boolean signedInByDefault() {
        return false; // each test signs in as the people it needs
    }

    private void tmdbHasTheSeries() {
        tmdbReturns("/movie/550", FILM);
        tmdbReturns("/collection/9001", COLLECTION);
    }

    private void onTheList(String username, long tmdbId, String title, WatchStatus status, Integer rating) {
        User user = userRepository.findByUsername(username).orElseThrow();
        WatchlistItem item = new WatchlistItem(user, tmdbId, "movie", title, null, 2000);
        item.update(status, rating, null);
        watchlistRepository.save(item);
    }

    @Test
    void aFilmInASeriesShowsTheWholeSeriesOldestFirstWithWhatIsOutAndWhatIsMissing() throws Exception {
        String token = register("fan1");
        tmdbHasTheSeries();

        mvc.perform(get("/api/titles/movie/550/franchise").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(9001))
                .andExpect(jsonPath("$.name").value("Hero Collection"))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.released").value(3))
                .andExpect(jsonPath("$.watched").value(0))
                .andExpect(jsonPath("$.missing").value(3))
                .andExpect(jsonPath("$.parts[*].title", Matchers.contains("Hero", "Hero II", "Hero III", "Hero IV", "Hero Untitled"))) // by date, undated last
                .andExpect(jsonPath("$.parts[0].year").value(2001))
                .andExpect(jsonPath("$.parts[3].released").value(false))
                .andExpect(jsonPath("$.parts[4].released").value(false))
                .andExpect(jsonPath("$.parts[0].status").doesNotExist());
    }

    @Test
    void yourOwnStatusAndTheProgressFollowFromYourWatchlist() throws Exception {
        String token = register("fan2");
        onTheList("fan2", 101, "Hero", WatchStatus.WATCHED, 9);
        onTheList("fan2", 102, "Hero II", WatchStatus.WANT, null);
        tmdbHasTheSeries();

        mvc.perform(get("/api/titles/movie/550/franchise").header("Authorization", token))
                .andExpect(jsonPath("$.watched").value(1))
                .andExpect(jsonPath("$.missing").value(1)) // only Hero III is released and not on the list
                .andExpect(jsonPath("$.parts[0].status").value("WATCHED"))
                .andExpect(jsonPath("$.parts[1].status").value("WANT"))
                .andExpect(jsonPath("$.parts[2].status").doesNotExist());
    }

    @Test
    void oneMansProgressIsNotAnotherMans() throws Exception {
        String ann = register("fan3");
        String bob = register("fan4");
        onTheList("fan3", 101, "Hero", WatchStatus.WATCHED, null);
        tmdbHasTheSeries();

        mvc.perform(get("/api/titles/movie/550/franchise").header("Authorization", ann)).andExpect(jsonPath("$.watched").value(1));
        mvc.perform(get("/api/titles/movie/550/franchise").header("Authorization", bob)).andExpect(jsonPath("$.watched").value(0)); // answered from the cache, no new TMDB call
    }

    @Test
    void aFilmInNoSeriesHasNoFranchise() throws Exception {
        String token = register("fan5");
        tmdbReturns("/movie/551", SOLO_FILM);

        mvc.perform(get("/api/titles/movie/551/franchise").header("Authorization", token)).andExpect(status().isNoContent());
    }

    @Test
    void addTheRestAddsOnlyReleasedFilmsNotYetOnTheListAndLeavesTheOthersAlone() throws Exception {
        String token = register("fan6");
        onTheList("fan6", 101, "Hero", WatchStatus.WATCHED, 9);
        onTheList("fan6", 102, "Hero II", WatchStatus.WATCHING, null);
        tmdbReturns("/collection/9001", COLLECTION);

        mvc.perform(post("/api/collections/9001/watchlist").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.added").value(1)) // Hero III
                .andExpect(jsonPath("$.alreadyOnList").value(2))
                .andExpect(jsonPath("$.notReleased").value(2)); // the 2999 film and the one with no date

        User user = userRepository.findByUsername("fan6").orElseThrow();
        assertThat(watchlistRepository.countByUser(user)).isEqualTo(3);
        WatchlistItem added = watchlistRepository.findByUserAndTmdbIdAndMediaType(user, 103L, "movie").orElseThrow();
        assertThat(added.getStatus()).isEqualTo(WatchStatus.WANT);
        assertThat(added.getTitle()).isEqualTo("Hero III");
        assertThat(added.getReleaseYear()).isEqualTo(2008);
        assertThat(added.getPosterPath()).isEqualTo("/p3.jpg");
        WatchlistItem untouched = watchlistRepository.findByUserAndTmdbIdAndMediaType(user, 101L, "movie").orElseThrow();
        assertThat(untouched.getStatus()).isEqualTo(WatchStatus.WATCHED);
        assertThat(untouched.getRating()).isEqualTo(9);
        assertThat(watchlistRepository.findByUserAndTmdbIdAndMediaType(user, 102L, "movie").orElseThrow().getStatus()).isEqualTo(WatchStatus.WATCHING);
        assertThat(watchlistRepository.findByUserAndTmdbIdAndMediaType(user, 104L, "movie")).isEmpty();
    }

    @Test
    void addingTheRestTwiceAddsNothingTheSecondTime() throws Exception {
        String token = register("fan7");
        tmdbReturns("/collection/9001", COLLECTION);

        mvc.perform(post("/api/collections/9001/watchlist").header("Authorization", token)).andExpect(jsonPath("$.added").value(3));
        mvc.perform(post("/api/collections/9001/watchlist").header("Authorization", token))
                .andExpect(jsonPath("$.added").value(0)).andExpect(jsonPath("$.alreadyOnList").value(3));
        assertThat(watchlistRepository.countByUser(userRepository.findByUsername("fan7").orElseThrow())).isEqualTo(3);
    }

    @Test
    void afterAddingTheRestNothingIsMissing() throws Exception {
        String token = register("fan8");
        tmdbHasTheSeries();
        mvc.perform(post("/api/collections/9001/watchlist").header("Authorization", token)).andExpect(status().isOk());

        mvc.perform(get("/api/titles/movie/550/franchise").header("Authorization", token))
                .andExpect(jsonPath("$.missing").value(0))
                .andExpect(jsonPath("$.parts[0].status").value("WANT"));
    }

    @Test
    void anUnknownCollectionIsNotFound() throws Exception {
        String token = register("fan9");
        tmdb.expect(once(), requestTo(Matchers.containsString("/collection/9"))).andRespond(withStatus(HttpStatus.NOT_FOUND));

        mvc.perform(post("/api/collections/9/watchlist").header("Authorization", token)).andExpect(status().isNotFound());
    }

    @Test
    void theTrackerNeedsALogin() throws Exception {
        anonymous().perform(get("/api/titles/movie/550/franchise")).andExpect(status().isUnauthorized());
        anonymous().perform(post("/api/collections/9001/watchlist")).andExpect(status().isUnauthorized());
    }
}
