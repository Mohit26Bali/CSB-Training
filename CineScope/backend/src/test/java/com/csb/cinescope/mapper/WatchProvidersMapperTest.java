package com.csb.cinescope.mapper;

import static com.csb.cinescope.TmdbFixtures.PROVIDERS;
import static com.csb.cinescope.TmdbFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.dto.WatchProvider;
import com.csb.cinescope.dto.WatchProviders;

/** Unit tests of {@link TitleMapper#providers}: TMDB's per-country availability turned into our four groups. */
class WatchProvidersMapperTest {

    TitleMapper mapper = new TitleMapper();

    private static List<String> names(List<WatchProvider> list) {
        return list.stream().map(WatchProvider::name).toList();
    }

    @Test
    void groupsAreFilledFromTheRequestedCountryOnly() {
        WatchProviders in = mapper.providers(json(PROVIDERS), "IN");

        assertEquals("IN", in.region());
        assertEquals("https://www.themoviedb.org/movie/550/watch?locale=IN", in.link());
        assertEquals(List.of("Apple TV"), names(in.rent()));
        assertEquals(List.of("Google Play Movies"), names(in.buy()));
        assertTrue(in.available());

        WatchProviders us = mapper.providers(json(PROVIDERS), "US");
        assertTrue(us.stream().isEmpty());
        assertEquals(List.of("Apple TV"), names(us.buy()));
    }

    @Test
    void servicesAreOrderedByPriorityAndNeverRepeatedInAGroup() {
        WatchProviders in = mapper.providers(json(PROVIDERS), "IN");

        assertEquals(List.of("Netflix", "JioHotstar"), names(in.stream()));
    }

    @Test
    void freeAndAdSupportedAreMergedWithoutDuplicates() {
        WatchProviders in = mapper.providers(json(PROVIDERS), "IN");

        assertEquals(List.of("MX Player", "Pluto"), names(in.free()));
        assertNull(in.free().get(1).logoPath()); // a missing logo stays null instead of failing
    }

    @Test
    void aCountryWithoutDataGivesEmptyGroupsAndNoLink() {
        WatchProviders none = mapper.providers(json(PROVIDERS), "JP");

        assertEquals("JP", none.region());
        assertNull(none.link());
        assertFalse(none.available());
        assertTrue(none.stream().isEmpty() && none.free().isEmpty() && none.rent().isEmpty() && none.buy().isEmpty());
    }

    @Test
    void anEmptyOrOddAnswerNeverThrows() {
        assertFalse(mapper.providers(json("{}"), "IN").available());
        assertFalse(mapper.providers(json("{\"results\":[]}"), "IN").available());
        assertFalse(mapper.providers(json("{\"results\":{\"IN\":{\"flatrate\":[{\"provider_id\":0},{\"provider_name\":\"No id\"},{\"provider_id\":9}]}}}"), "IN").available());
    }
}
