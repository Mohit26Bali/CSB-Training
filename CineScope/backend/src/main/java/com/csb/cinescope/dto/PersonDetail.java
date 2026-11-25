package com.csb.cinescope.dto;

import java.util.List;

/**
 * An actor, director or other film person: who they are and what they worked on. Dates are ISO text
 * ({@code 1962-07-03}); {@code deathday} is null for living people. {@code knownFor} is the handful of best-known titles
 * (shown as posters); {@code acting} and {@code crew} are the full filmography, newest first, each title once.
 * Talk shows and news programmes are left out, because for popular people they bury the real work.
 *
 * <p>Returned by {@code GET /api/people/{id}} ({@code controller/CatalogController}), built by
 * {@code mapper/TitleMapper#person} from TMDB's person answer with {@code combined_credits} appended. The ids in
 * {@link CastMember} and {@link CrewMember} (title pages) and in search results point here.
 */
public record PersonDetail(
        long id,
        String name,
        String biography,
        String birthday,
        String deathday,
        String placeOfBirth,
        String knownForDepartment,
        String profilePath,
        String homepage,
        List<PersonCredit> knownFor,
        List<PersonCredit> acting,
        List<PersonCredit> crew) {
}
