package com.csb.cinescope.dto;

import com.csb.cinescope.entity.WatchStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/watchlist/{type}/{id}}: the user's status for a title, plus an optional rating and note. The
 * request REPLACES all three, so sending status WANT without a rating also clears an earlier rating. A rating is only
 * accepted together with status WATCHED ({@code service/WatchlistService} answers 400 otherwise).
 */
public record WatchlistEntryRequest(
        @NotNull(message = "status is required (WANT, WATCHING or WATCHED)") WatchStatus status,
        @Min(value = 1, message = "rating must be between 1 and 10") @Max(value = 10, message = "rating must be between 1 and 10") Integer rating,
        @Size(max = 500, message = "note must be at most 500 characters") String note) {
}
