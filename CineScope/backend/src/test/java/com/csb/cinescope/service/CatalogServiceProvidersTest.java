package com.csb.cinescope.service;

import static com.csb.cinescope.TmdbFixtures.PROVIDERS;
import static com.csb.cinescope.TmdbFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.SortedMap;
import java.util.concurrent.ExecutorService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.csb.cinescope.config.CineScopeProperties;
import com.csb.cinescope.dto.WatchProviders;
import com.csb.cinescope.exception.BadRequestException;
import com.csb.cinescope.mapper.TitleMapper;
import com.csb.cinescope.tmdb.TmdbGateway;

/**
 * Unit tests of {@link CatalogService#providers}: which TMDB path is asked (with no parameters, so one cached answer
 * serves every country), the India default, region normalisation and validation before TMDB is called.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceProvidersTest {

    @Mock TmdbGateway tmdb;
    @Mock ExecutorService executor;

    private CatalogService service() {
        return new CatalogService(tmdb, new TitleMapper(), new CineScopeProperties("IN", "en-US"), executor);
    }

    @Test
    void asksTmdbForTheWatchProvidersPathWithoutParametersAndDefaultsToIndia() {
        when(tmdb.detail(eq("/movie/550/watch/providers"), any())).thenReturn(json(PROVIDERS));

        WatchProviders result = service().providers("movie", 550, null);

        assertEquals("IN", result.region());
        assertTrue(result.available());
        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<SortedMap<String, String>> query = org.mockito.ArgumentCaptor.forClass(SortedMap.class);
        verify(tmdb).detail(eq("/movie/550/watch/providers"), query.capture());
        assertTrue(query.getValue().isEmpty(), "no language/region parameter: the answer must be the same for every country");
    }

    @Test
    void aBlankRegionMeansTheDefaultAndALowercaseOneIsNormalised() {
        when(tmdb.detail(eq("/tv/1/watch/providers"), any())).thenReturn(json(PROVIDERS));

        assertEquals("IN", service().providers("tv", 1, "  ").region());
        assertEquals("US", service().providers("tv", 1, " us ").region());
    }

    @Test
    void invalidInputIsRejectedWithoutCallingTmdb() {
        for (String region : new String[] { "India", "1N", "I", "IN;" }) {
            assertThrows(BadRequestException.class, () -> service().providers("movie", 1, region));
        }
        assertThrows(BadRequestException.class, () -> service().providers("book", 1, "IN"));
        assertThrows(BadRequestException.class, () -> service().providers("movie", 0, "IN"));
        verifyNoInteractions(tmdb);
    }
}
