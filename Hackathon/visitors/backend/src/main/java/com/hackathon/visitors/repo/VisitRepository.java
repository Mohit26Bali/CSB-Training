package com.hackathon.visitors.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.visitors.entity.User;
import com.hackathon.visitors.entity.Visit;
import com.hackathon.visitors.entity.VisitStatus;

import jakarta.persistence.LockModeType;

public interface VisitRepository extends JpaRepository<Visit, Long> {

    boolean existsByPassCode(String passCode);

    List<Visit> findByHostAndExpectedDateAndStatusNot(User host, LocalDate expectedDate, VisitStatus status);

    @EntityGraph(attributePaths = { "host" })
    List<Visit> findByHostOrderByExpectedDateDescCreatedAtDesc(User host);

    @EntityGraph(attributePaths = { "host" })
    List<Visit> findAllByOrderByExpectedDateDescExpectedTimeAscIdAsc();

    @EntityGraph(attributePaths = { "host" })
    Optional<Visit> findWithHostByPassCode(String passCode);

    @EntityGraph(attributePaths = { "host" })
    Optional<Visit> findWithHostById(Long id);

    /** Row lock so two guards (or a guard and the host cancelling) cannot change one visit at once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Visit v where v.id = :id")
    Optional<Visit> findByIdForUpdate(@Param("id") Long id);
}
