package com.hackathon.referral.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.referral.entity.Job;
import com.hackathon.referral.entity.JobStatus;

import jakarta.persistence.LockModeType;

public interface JobRepository extends JpaRepository<Job, Long> {

    List<Job> findByStatusOrderByPublishedAtDesc(JobStatus status);

    List<Job> findAllByOrderByCreatedAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from Job j where j.id = :id")
    Optional<Job> findByIdForUpdate(@Param("id") Long id);
}
