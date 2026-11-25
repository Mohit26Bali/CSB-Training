package com.csb.cinescope.repository;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.jpa.domain.Specification;

import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;

import jakarta.persistence.criteria.Predicate;

/**
 * Builds the WHERE clause of the watchlist listing: always the owner, plus an optional status and media type.
 * Used by {@code service/WatchlistService#list} with {@link WatchlistItemRepository}. Because the owner condition is
 * unconditional, no listing can ever include another user's items.
 */
public final class WatchlistSpecifications {

    private WatchlistSpecifications() {
    }

    public static Specification<WatchlistItem> forUser(User user, WatchStatus status, String mediaType) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(root.get("user"), user));
            if (status != null) {
                conditions.add(cb.equal(root.get("status"), status));
            }
            if (mediaType != null) {
                conditions.add(cb.equal(root.get("mediaType"), mediaType));
            }
            return cb.and(conditions.toArray(Predicate[]::new));
        };
    }
}
