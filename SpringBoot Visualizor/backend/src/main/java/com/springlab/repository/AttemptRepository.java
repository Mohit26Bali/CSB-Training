package com.springlab.repository;

import com.springlab.entity.Attempt;
import com.springlab.entity.AttemptStatus;
import com.springlab.repository.projection.AttemptTotals;
import com.springlab.repository.projection.BreakdownRow;
import com.springlab.repository.projection.LeaderboardRow;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AttemptRepository extends JpaRepository<Attempt, Long> {

    /** Ownership is part of the query: another user's attempt is simply "not found". */
    @EntityGraph(attributePaths = "topic")
    Optional<Attempt> findByIdAndUserId(Long id, Long userId);

    /**
     * Locks the attempt row (SELECT ... FOR UPDATE) for the whole transaction. A concurrent second submission waits,
     * then sees status SUBMITTED and is rejected, so an attempt can never be scored twice.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Attempt a where a.id = :id and a.user.id = :userId")
    Optional<Attempt> findOwnedForUpdate(@Param("id") Long id, @Param("userId") Long userId);

    @EntityGraph(attributePaths = "topic")
    Page<Attempt> findByUserId(Long userId, Pageable pageable);

    @EntityGraph(attributePaths = "topic")
    List<Attempt> findTop5ByUserIdAndStatusOrderBySubmittedAtDesc(Long userId, AttemptStatus status);

    @Query("""
            select new com.springlab.repository.projection.AttemptTotals(
                   count(a), coalesce(sum(a.score), 0L), coalesce(sum(a.totalQuestions), 0L))
            from Attempt a where a.user.id = :userId and a.status = com.springlab.entity.AttemptStatus.SUBMITTED
            """)
    AttemptTotals totalsFor(@Param("userId") Long userId);

    @Query("""
            select new com.springlab.repository.projection.BreakdownRow(t.slug, t.name, count(aa),
                   sum(case when aa.correct = true then 1L else 0L end))
            from AttemptAnswer aa join aa.attempt a join aa.question q join q.topic t
            where a.user.id = :userId and a.status = com.springlab.entity.AttemptStatus.SUBMITTED
            group by t.id, t.slug, t.name order by t.name
            """)
    List<BreakdownRow> breakdownByTopic(@Param("userId") Long userId);

    @Query("""
            select new com.springlab.repository.projection.BreakdownRow(str(q.difficulty), str(q.difficulty), count(aa),
                   sum(case when aa.correct = true then 1L else 0L end))
            from AttemptAnswer aa join aa.attempt a join aa.question q
            where a.user.id = :userId and a.status = com.springlab.entity.AttemptStatus.SUBMITTED
            group by q.difficulty order by q.difficulty
            """)
    List<BreakdownRow> breakdownByDifficulty(@Param("userId") Long userId);

    /** Students only; disabled accounts and unsubmitted attempts are excluded. topicSlug null = all topics. */
    @Query("""
            select new com.springlab.repository.projection.LeaderboardRow(
                   u.username, count(a), sum(a.score), sum(a.totalQuestions))
            from Attempt a join a.user u left join a.topic t
            where a.status = com.springlab.entity.AttemptStatus.SUBMITTED
              and u.enabled = true and u.role = com.springlab.entity.Role.STUDENT
              and (:topicSlug is null or t.slug = :topicSlug)
            group by u.id, u.username
            order by sum(a.score) desc, sum(a.totalQuestions) asc, u.username asc
            """)
    List<LeaderboardRow> leaderboard(@Param("topicSlug") String topicSlug, Pageable pageable);
}
