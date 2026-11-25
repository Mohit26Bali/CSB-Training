package com.csb.cinescope.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.csb.cinescope.entity.QuizRound;

/** Queries on {@link QuizRound}, used by {@code service/QuizService}. */
public interface QuizRoundRepository extends JpaRepository<QuizRound, Long> {

    /** Each person's best finished score in a category, as (user id, best score) pairs. Unfinished rounds never count. */
    @Query("select r.userId, max(r.score) from QuizRound r where r.category = :category and r.finishedAt is not null group by r.userId")
    List<Object[]> bestScores(@Param("category") String category);
}
