package com.csb.cinescope.dto;

/** How many titles a user has in total and per status; drives the tab counts of the watchlist page. */
public record WatchlistSummary(long total, long want, long watching, long watched) {
}
