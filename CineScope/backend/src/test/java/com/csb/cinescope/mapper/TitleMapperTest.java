package com.csb.cinescope.mapper;

import static com.csb.cinescope.TmdbFixtures.GENRES_MOVIE;
import static com.csb.cinescope.TmdbFixtures.MOVIE_DETAIL;
import static com.csb.cinescope.TmdbFixtures.POPULAR_MOVIES;
import static com.csb.cinescope.TmdbFixtures.SEARCH_MULTI;
import static com.csb.cinescope.TmdbFixtures.TRENDING_ALL;
import static com.csb.cinescope.TmdbFixtures.TV_DETAIL;
import static com.csb.cinescope.TmdbFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.dto.PageResult;
import com.csb.cinescope.dto.SearchItem;
import com.csb.cinescope.dto.TitleDetail;
import com.csb.cinescope.dto.TitleSummary;

/**
 * Unit tests of {@link TitleMapper}: TMDB's awkward JSON in, clean records out. Pure logic, no Spring, no network.
 */
class TitleMapperTest {

    TitleMapper mapper = new TitleMapper();

    // ---- lists ----

    @Test
    void trendingMixesMoviesAndShowsUsingEachItemsOwnType_andDropsPeople() {
        PageResult<TitleSummary> page = mapper.summaryPage(json(TRENDING_ALL), "movie");

        assertEquals(3, page.items().size()); // movie, tv, movie; the person is dropped
        assertEquals("movie", page.items().get(0).type());
        assertEquals("tv", page.items().get(1).type());
        assertEquals(1, page.page());
        assertEquals(3, page.totalPages());
        assertEquals(60, page.totalResults());
    }

    @Test
    void aShowUsesItsNameAndFirstAirDateWhereAMovieUsesTitleAndReleaseDate() {
        TitleSummary movie = mapper.summaryPage(json(TRENDING_ALL), "movie").items().get(0);
        TitleSummary show = mapper.summaryPage(json(TRENDING_ALL), "movie").items().get(1);

        assertEquals("The Last Lighthouse", movie.title());
        assertEquals(2026, movie.year());
        assertEquals("Harbor Lights", show.title());
        assertEquals(2025, show.year());
        assertEquals(8.1, show.rating(), 0.0001);
    }

    @Test
    void missingImagesEmptyDatesAndEmptyOverviewsBecomeNull_notErrors() {
        TitleSummary bare = mapper.summaryPage(json(TRENDING_ALL), "movie").items().get(2);

        assertEquals("Mystery Reel", bare.title());
        assertNull(bare.posterPath());
        assertNull(bare.backdropPath());
        assertNull(bare.year());
        assertNull(bare.overview());
        assertEquals(0, bare.rating(), 0.0001);
    }

    @Test
    void itemsWithoutAMediaTypeGetTheCallersDefaultType() {
        PageResult<TitleSummary> page = mapper.summaryPage(json(POPULAR_MOVIES), "movie");

        assertEquals(List.of("movie", "movie"), page.items().stream().map(TitleSummary::type).toList());
        assertEquals("/a.jpg", page.items().get(0).posterPath());
    }

    @Test
    void totalPagesIsCappedAtTmdbsLimitOf500() {
        PageResult<TitleSummary> page = mapper.summaryPage(json(POPULAR_MOVIES), "movie"); // fixture says 900 pages
        assertEquals(500, page.totalPages());
    }

    @Test
    void anEmptyOrUnexpectedAnswerGivesAnEmptyPage() {
        PageResult<TitleSummary> page = mapper.summaryPage(json("{}"), "movie");

        assertTrue(page.items().isEmpty());
        assertEquals(1, page.page());
        assertEquals(1, page.totalPages());
    }

    // ---- search ----

    @Test
    void searchKeepsMoviesShowsAndPeopleAndDropsOtherKinds() {
        PageResult<SearchItem> page = mapper.searchPage(json(SEARCH_MULTI));

        assertEquals(List.of("person", "movie", "tv"), page.items().stream().map(SearchItem::type).toList());
    }

    @Test
    void aPersonHitCarriesTheirPhotoAndUpToThreeKnownForTitles() {
        SearchItem person = mapper.searchPage(json(SEARCH_MULTI)).items().get(0);

        assertEquals("Christopher Nolan", person.title());
        assertEquals("/cn.jpg", person.imagePath());
        assertEquals("Inception, Interstellar, A Show", person.knownFor()); // the fourth is cut
        assertNull(person.year());
    }

    @Test
    void aTitleHitCarriesPosterYearAndRating() {
        SearchItem movie = mapper.searchPage(json(SEARCH_MULTI)).items().get(1);

        assertEquals("Inception", movie.title());
        assertEquals("/inc.jpg", movie.imagePath());
        assertEquals(2010, movie.year());
        assertEquals(8.4, movie.rating(), 0.0001);
    }

    // ---- genres ----

    @Test
    void genresSkipEntriesWithoutAnIdOrAName() {
        assertEquals(2, mapper.genres(json(GENRES_MOVIE)).size());
        assertEquals("Action", mapper.genres(json(GENRES_MOVIE)).get(0).name());
    }

    // ---- movie detail ----

    @Test
    void movieDetailCarriesTheFactsOfTheMovie() {
        TitleDetail d = mapper.detail(json(MOVIE_DETAIL), "movie", "IN");

        assertEquals(550, d.id());
        assertEquals("Fight Club", d.title());
        assertEquals("Mischief. Mayhem. Soap.", d.tagline());
        assertEquals(1999, d.year());
        assertEquals("1999-10-15", d.releaseDate());
        assertEquals(139, d.runtime());
        assertEquals(28000, d.voteCount());
        assertEquals("Released", d.status());
        assertEquals("en", d.language());
        assertEquals(63_000_000L, d.budget());
        assertEquals(100_853_753L, d.revenue());
        assertNull(d.homepage()); // blank in the data
        assertNull(d.seasons()); // movies have no seasons
        assertEquals(List.of("Drama", "Thriller"), d.genres().stream().map(g -> g.name()).toList());
    }

    @Test
    void movieDetailFindsTheDirectorAndCast() {
        TitleDetail d = mapper.detail(json(MOVIE_DETAIL), "movie", "IN");

        assertEquals(1, d.directors().size());
        assertEquals("David Fincher", d.directors().get(0).name());
        assertEquals("Director", d.directors().get(0).job());
        assertEquals(List.of("Edward Norton", "Brad Pitt"), d.cast().stream().map(c -> c.name()).toList());
        assertEquals("The Narrator", d.cast().get(0).character());
        assertNull(d.cast().get(1).profilePath());
    }

    @Test
    void aTitleIsFamilyFriendlyOnlyWhenItsRatingMeansEveryone() {
        // MOVIE_DETAIL is rated A in India and R in the US
        assertFalse(mapper.detail(json(MOVIE_DETAIL), "movie", "IN").familyFriendly());
        String kids = "{\"id\":9,\"title\":\"Kids\",\"release_dates\":{\"results\":[{\"iso_3166_1\":\"IN\",\"release_dates\":[{\"certification\":\"U\"}]}]}}";
        assertTrue(mapper.detail(json(kids), "movie", "IN").familyFriendly());
        String unrated = "{\"id\":9,\"title\":\"Unrated\"}";
        assertFalse(mapper.detail(json(unrated), "movie", "IN").familyFriendly()); // no rating is not safe
        String cartoon = "{\"id\":9,\"name\":\"Cartoon\",\"content_ratings\":{\"results\":[{\"iso_3166_1\":\"US\",\"rating\":\"TV-Y7\"}]}}";
        assertTrue(mapper.detail(json(cartoon), "tv", "IN").familyFriendly());
    }

    @Test
    void certificationPrefersTheRegionAndSkipsBlankEntries() {
        assertEquals("A", mapper.detail(json(MOVIE_DETAIL), "movie", "IN").certification());
        assertEquals("R", mapper.detail(json(MOVIE_DETAIL), "movie", "US").certification());
        // a region with no entry falls back to the US one
        assertEquals("R", mapper.detail(json(MOVIE_DETAIL), "movie", "FR").certification());
    }

    @Test
    void videosKeepOnlyYouTubeTrailersAndTeasers_officialTrailersFirst() {
        TitleDetail d = mapper.detail(json(MOVIE_DETAIL), "movie", "IN");

        assertEquals(List.of("trailer1", "teaser1"), d.videos().stream().map(v -> v.key()).toList());
        assertTrue(d.videos().get(0).official());
    }

    @Test
    void recommendationsAreUsedAsSimilarTitles() {
        TitleDetail d = mapper.detail(json(MOVIE_DETAIL), "movie", "IN");

        assertEquals(List.of("Pulp Fiction", "Se7en"), d.similar().stream().map(TitleSummary::title).toList());
        assertEquals("movie", d.similar().get(0).type());
    }

    // ---- tv detail ----

    @Test
    void tvDetailHasSeasonsEpisodesAndCreatorsInsteadOfBudgetAndDirector() {
        TitleDetail d = mapper.detail(json(TV_DETAIL), "tv", "IN");

        assertEquals("Breaking Bad", d.title());
        assertEquals(2008, d.year());
        assertEquals(47, d.runtime()); // first episode runtime
        assertEquals(5, d.seasons());
        assertEquals(62, d.episodes());
        assertNull(d.budget());
        assertNull(d.revenue());
        assertEquals("Vince Gilligan", d.directors().get(0).name());
        assertEquals("Creator", d.directors().get(0).job());
        assertEquals("https://example.test/bb", d.homepage());
    }

    @Test
    void tvRatingFallsBackToTheUsOneAndSimilarFallsBackWhenThereAreNoRecommendations() {
        TitleDetail d = mapper.detail(json(TV_DETAIL), "tv", "IN");

        assertEquals("TV-MA", d.certification());
        assertEquals(List.of("Better Call Saul"), d.similar().stream().map(TitleSummary::title).toList());
        assertEquals("tv", d.similar().get(0).type());
    }

    @Test
    void aMinimalDetailAnswerDoesNotBreakTheMapper() {
        TitleDetail d = mapper.detail(json("{\"id\":7,\"title\":\"Bare\"}"), "movie", "IN");

        assertEquals("Bare", d.title());
        assertTrue(d.cast().isEmpty());
        assertTrue(d.videos().isEmpty());
        assertTrue(d.similar().isEmpty());
        assertTrue(d.genres().isEmpty());
        assertNull(d.runtime());
        assertNull(d.certification());
    }
}
