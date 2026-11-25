package com.csb.cinescope.dto;

import java.time.LocalDateTime;

import com.csb.cinescope.entity.WatchStatus;

/**
 * One watchlist item as the client sees it. {@code type} ("movie" or "tv") with {@code tmdbId} is the same identity the
 * catalogue uses, so an entry links straight to {@code /title/{type}/{id}}. Built by {@code service/WatchlistService}
 * from an {@code entity/WatchlistItem}.
 */
public record WatchlistEntry(
        long tmdbId,
        String type,
        String title,
        String posterPath,
        Integer year,
        WatchStatus status,
        Integer rating,
        String note,
        LocalDateTime addedAt,
        LocalDateTime updatedAt) {
}
