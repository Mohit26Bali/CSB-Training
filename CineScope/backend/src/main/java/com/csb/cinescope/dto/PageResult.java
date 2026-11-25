package com.csb.cinescope.dto;

import java.util.List;

/**
 * One page of a longer list (browse, discover, search). {@code page} starts at 1, like TMDB. TMDB serves at most 500
 * pages per list, so {@code totalPages} is capped at 500 by {@code mapper/TitleMapper}.
 */
public record PageResult<T>(List<T> items, int page, int totalPages, long totalResults) {
}
