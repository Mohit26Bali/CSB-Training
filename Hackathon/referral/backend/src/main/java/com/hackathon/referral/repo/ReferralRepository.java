package com.hackathon.referral.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.Referral;
import com.hackathon.referral.entity.User;

import jakarta.persistence.LockModeType;

public interface ReferralRepository extends JpaRepository<Referral, Long> {

    boolean existsByJobAndCandidateEmail(Job job, String candidateEmail);

    long countByJob(Job job);

    @EntityGraph(attributePaths = { "job", "referrer" })
    List<Referral> findByReferrerOrderBySubmittedAtDesc(User referrer);

    @EntityGraph(attributePaths = { "job", "referrer" })
    List<Referral> findAllByOrderBySubmittedAtDesc();

    /** Row lock so two recruiters cannot move the same referral at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Referral r where r.id = :id")
    Optional<Referral> findByIdForUpdate(@Param("id") Long id);
}
