package com.csb.cinescope.dto;

/**
 * What "add the rest to my watchlist" did: {@code added} films put on the list as want-to-watch, {@code alreadyOnList} left alone
 * (their status and rating are never touched), and {@code notReleased} skipped because they are not out yet. Returned by
 * {@code POST /api/collections/{id}/watchlist}.
 */
public record FranchiseAddResult(int added, int alreadyOnList, int notReleased) {
}
