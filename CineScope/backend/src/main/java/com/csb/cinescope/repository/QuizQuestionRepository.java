package com.csb.cinescope.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.csb.cinescope.entity.QuizQuestion;

/** Queries on {@link QuizQuestion}, used by {@code service/QuizService}. */
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    Optional<QuizQuestion> findByRoundIdAndSeq(long roundId, int seq);

    List<QuizQuestion> findByRoundIdOrderBySeq(long roundId);
}
