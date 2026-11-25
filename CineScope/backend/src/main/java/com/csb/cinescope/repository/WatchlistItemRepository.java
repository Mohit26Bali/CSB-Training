package com.csb.cinescope.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.cinescope.entity.User;
import com.csb.cinescope.entity.WatchStatus;
import com.csb.cinescope.entity.WatchlistItem;

/**
 * Queries on {@link WatchlistItem}, used only by {@code service/WatchlistService}.
 *
 * <p>Every method takes the {@link User}: a user can only ever reach their own rows, because there is no query that finds an
 * item without naming its owner. Filtered, sorted, paged listing goes through {@code JpaSpecificationExecutor} with the
 * conditions built by {@link WatchlistSpecifications}.
 */
public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Long>, JpaSpecificationExecutor<WatchlistItem> {

    Optional<WatchlistItem> findByUserAndTmdbIdAndMediaType(User user, Long tmdbId, String mediaType);

    /** Deletes the item and returns how many rows were removed (0 when it was not on the list). */
    long deleteByUserAndTmdbIdAndMediaType(User user, Long tmdbId, String mediaType);

    /** How many items the user has per status, for the tab counts of the watchlist page. */
    @Query("select w.status as status, count(w) as total from WatchlistItem w where w.user = :user group by w.status")
    List<StatusCount> countByStatus(@Param("user") User user);

    /** The user's listed titles of one type with their status, for {@code service/RecommendationService} (what to skip or prefer). */
    @Query("select w.tmdbId as tmdbId, w.status as status from WatchlistItem w where w.user = :user and w.mediaType = :type")
    List<Tagged> findTagged(@Param("user") User user, @Param("type") String type);

    interface Tagged {
        Long getTmdbId();

        WatchStatus getStatus();
    }

    /** Removes every item of the user (before the account itself is deleted) and returns how many rows went. */
    long deleteByUser(User user);

    long countByUser(User user);

    /** Watchlist sizes for a page of users in one query, for the administrator's user list. */
    @Query("select w.user.id as userId, count(w) as total from WatchlistItem w where w.user.id in :ids group by w.user.id")
    List<UserCount> countByUserIds(@Param("ids") java.util.Collection<Long> ids);

    interface UserCount {
        Long getUserId();

        long getTotal();
    }

    interface StatusCount {
        WatchStatus getStatus();

        long getTotal();
    }
}
