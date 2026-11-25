package com.csb.cinescope.entity;

/**
 * Where a title stands for a user: {@code WANT} (on the list, not started), {@code WATCHING} (in progress) or
 * {@code WATCHED} (finished; only then may it carry a rating). Stored on {@link WatchlistItem#getStatus()} as text; the
 * rule "a rating needs WATCHED" is enforced by {@code service/WatchlistService}.
 */
public enum WatchStatus {
    WANT,
    WATCHING,
    WATCHED
}
