package com.csb.cinescope.dto;

import java.util.List;

/**
 * Everything the home page needs in one request: the titles of the rotating banner at the top ({@code heroes}, up to six
 * of today's trending titles that have a backdrop picture and a description), the first of them again as {@code hero} for
 * older callers, and the rows below it. Built by {@code service/CatalogService#home}, which fetches the rows from TMDB in
 * parallel.
 */
public record HomeResponse(TitleSummary hero, List<TitleSummary> heroes, List<HomeRow> rows) {
}
