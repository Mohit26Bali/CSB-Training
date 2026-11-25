package com.csb.cinescope.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.cinescope.entity.CommentVote;
import com.csb.cinescope.entity.User;

/** Queries on {@link CommentVote} ("helpful" marks), used by {@code service/CommentService}. */
public interface CommentVoteRepository extends JpaRepository<CommentVote, Long> {

    @Query("select v from CommentVote v where v.comment.id = :commentId and v.user.id = :userId")
    Optional<CommentVote> find(@Param("commentId") long commentId, @Param("userId") long userId);

    /** Of these comments, the ones this person has marked helpful (one query for a whole page). */
    @Query("select v.comment.id from CommentVote v where v.user.id = :userId and v.comment.id in :commentIds")
    List<Long> votedAmong(@Param("userId") long userId, @Param("commentIds") Collection<Long> commentIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CommentVote v where v.comment.id in :commentIds")
    int deleteOnComments(@Param("commentIds") Collection<Long> commentIds);

    /** The comments this person has marked helpful. */
    @Query("select v.comment.id from CommentVote v where v.user = :user")
    List<Long> commentIdsMarkedBy(@Param("user") User user);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CommentVote v where v.user = :user")
    int deleteCastBy(@Param("user") User user);
}
