package com.csb.cinescope.mapper;

import static com.csb.cinescope.TmdbFixtures.PERSON;
import static com.csb.cinescope.TmdbFixtures.json;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.csb.cinescope.dto.PersonCredit;
import com.csb.cinescope.dto.PersonDetail;

/** Unit tests of {@link TitleMapper#person}: TMDB's person answer turned into the profile and filmography we serve. */
class PersonMapperTest {

    TitleMapper mapper = new TitleMapper();

    private static List<String> titles(List<PersonCredit> credits) {
        return credits.stream().map(PersonCredit::title).toList();
    }

    @Test
    void profileFieldsAreCopiedAndBlankOnesBecomeNull() {
        PersonDetail p = mapper.person(json(PERSON));

        assertEquals(3001, p.id());
        assertEquals("Mara Whitfield", p.name());
        assertEquals("Born in a lighthouse.", p.biography());
        assertEquals("1975-04-12", p.birthday());
        assertNull(p.deathday());
        assertEquals("Leeds, England", p.placeOfBirth());
        assertEquals("Acting", p.knownForDepartment());
        assertEquals("/mw.jpg", p.profilePath());
        assertNull(p.homepage()); // "" in TMDB's answer
    }

    @Test
    void actingIsNewestFirstUndatedLastAndDropsTalkShowsAndNonTitles() {
        PersonDetail p = mapper.person(json(PERSON));

        assertEquals(List.of("New Film", "Long Show", "Minor Part", "Famous Sitcom", "Old Film", "Unreleased"), titles(p.acting()));
        assertEquals("Eve", p.acting().get(0).role());
        assertEquals("tv", p.acting().get(1).type());
        assertEquals(2010, p.acting().get(1).year());
        assertNull(p.acting().get(5).year());
        assertNull(p.acting().get(5).role());
    }

    @Test
    void severalJobsOnOneTitleBecomeOneCrewLine() {
        PersonDetail p = mapper.person(json(PERSON));

        assertEquals(List.of("New Film", "Behind Scenes"), titles(p.crew()));
        assertEquals("Director, Writer", p.crew().get(0).role());
        assertEquals("Producer", p.crew().get(1).role());
    }

    @Test
    void knownForIsTheMostVotedRealWorkWithAPosterAcrossActingAndCrewWithoutRepeats() {
        PersonDetail p = mapper.person(json(PERSON));

        // "Famous Sitcom" (1 guest episode) and "Minor Part" (billed 41st) have the most votes but are not real work
        assertEquals(List.of("Long Show", "New Film", "Behind Scenes", "Old Film", "Unreleased"), titles(p.knownFor()));
    }

    @Test
    void aPersonWithoutCreditsOrDetailsGivesEmptyListsNotAnError() {
        PersonDetail p = mapper.person(json("{\"id\":9,\"name\":\"Nobody\"}"));

        assertEquals("Nobody", p.name());
        assertNull(p.biography());
        assertTrue(p.knownFor().isEmpty() && p.acting().isEmpty() && p.crew().isEmpty());
    }

    @Test
    void veryLongCareersAreCapped() {
        StringBuilder cast = new StringBuilder();
        for (int i = 1; i <= 260; i++) {
            cast.append(i > 1 ? "," : "").append("{\"id\":").append(i).append(",\"media_type\":\"movie\",\"title\":\"T").append(i)
                    .append("\",\"release_date\":\"2000-01-01\",\"genre_ids\":[]}");
        }
        PersonDetail p = mapper.person(json("{\"id\":9,\"name\":\"Busy\",\"combined_credits\":{\"cast\":[" + cast + "]}}"));

        assertEquals(200, p.acting().size());
        assertTrue(p.knownFor().isEmpty()); // no posters
    }
}
