package com.springlab.repository;

import com.springlab.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    @Query("""
            select aa from AttemptAnswer aa
            join fetch aa.question q join fetch q.topic left join fetch aa.selectedOption
            where aa.attempt.id = :attemptId order by aa.position
            """)
    List<AttemptAnswer> findDetailedByAttemptId(@Param("attemptId") Long attemptId);
}
