package com.csb.cinescope.repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.cinescope.entity.Comment;
import com.csb.cinescope.entity.CommentStatus;
import com.csb.cinescope.entity.Sentiment;
import com.csb.cinescope.entity.User;

/**
 * Queries on {@link Comment}, used by {@code service/CommentService} (and the demo-data seeder). "Visible" always means what the
 * person asking may see: every PUBLISHED comment, plus their own PENDING and REJECTED ones. Deletions are written as "delete these
 * ids" because MySQL refuses a DELETE whose sub-select reads the same table; the service collects the ids first.
 */
public interface CommentRepository extends JpaRepository<Comment, Long> {

    String VISIBLE = "(c.status = com.csb.cinescope.entity.CommentStatus.PUBLISHED or "
            + "(c.user.id = :me and c.status in (com.csb.cinescope.entity.CommentStatus.PENDING, com.csb.cinescope.entity.CommentStatus.REJECTED)))";

    @EntityGraph(attributePaths = "user")
    @Query("select c from Comment c where c.mediaType = :type and c.tmdbId = :id and c.parent is null and " + VISIBLE)
    Page<Comment> findTopLevel(@Param("type") String type, @Param("id") long id, @Param("me") long me, Pageable pageable);

    @EntityGraph(attributePaths = { "user", "parent" })
    @Query("select c from Comment c where c.parent.id in :parentIds and " + VISIBLE + " order by c.createdAt asc, c.id asc")
    List<Comment> findReplies(@Param("parentIds") Collection<Long> parentIds, @Param("me") long me);

    @Query("select c from Comment c join fetch c.user left join fetch c.parent where c.id = :id")
    Optional<Comment> load(@Param("id") long id);

    @EntityGraph(attributePaths = { "user", "parent" })
    Page<Comment> findByStatusOrderByCreatedAtDescIdDesc(CommentStatus status, Pageable pageable);

    // ---- numbers for the discussion summary (published comments only) ----

    long countByMediaTypeAndTmdbIdAndParentIsNullAndStatus(String mediaType, Long tmdbId, CommentStatus status);

    long countByMediaTypeAndTmdbIdAndParentIsNotNullAndStatus(String mediaType, Long tmdbId, CommentStatus status);

    @Query("select c.rating from Comment c where c.mediaType = :type and c.tmdbId = :id and c.parent is null and c.rating is not null "
            + "and c.status = com.csb.cinescope.entity.CommentStatus.PUBLISHED")
    List<Integer> publishedRatings(@Param("type") String type, @Param("id") long id);

    @Query("select c.sentiment as sentiment, count(c) as total from Comment c where c.mediaType = :type and c.tmdbId = :id "
            + "and c.status = com.csb.cinescope.entity.CommentStatus.PUBLISHED group by c.sentiment")
    List<SentimentCount> sentimentCounts(@Param("type") String type, @Param("id") long id);

    @Query("select c.body from Comment c where c.mediaType = :type and c.tmdbId = :id and c.status = com.csb.cinescope.entity.CommentStatus.PUBLISHED "
            + "order by c.createdAt desc")
    List<String> recentBodies(@Param("type") String type, @Param("id") long id, Pageable pageable);

    interface SentimentCount {
        Sentiment getSentiment();

        long getTotal();
    }

    // ---- rules about one person ----

    long countByUserAndCreatedAtAfter(User user, LocalDateTime since);

    boolean existsByUserAndBodyAndCreatedAtAfter(User user, String body, LocalDateTime since);

    boolean existsByUserAndMediaTypeAndTmdbIdAndParentIsNullAndRatingIsNotNull(User user, String mediaType, Long tmdbId);

    // ---- deleting (see the class comment) ----

    @Query("select c.id from Comment c where c.user = :user")
    List<Long> idsOfUser(@Param("user") User user);

    @Query("select c.id from Comment c where c.parent.id in :parentIds")
    List<Long> idsOfRepliesTo(@Param("parentIds") Collection<Long> parentIds);

    /** Takes one off the stored helpful count of these comments (used when a person's marks on them are removed). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Comment c set c.helpfulCount = c.helpfulCount - 1 where c.id in :ids and c.helpfulCount > 0")
    int decrementHelpful(@Param("ids") Collection<Long> ids);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Comment c where c.id in :ids")
    int deleteByIds(@Param("ids") Collection<Long> ids);
}
