package com.hackathon.appointment.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.appointment.entity.Provider;
import com.hackathon.appointment.entity.Slot;

import jakarta.persistence.LockModeType;

public interface SlotRepository extends JpaRepository<Slot, Long> {

    /** Row lock: bookings, confirmations and cancellations for one slot are handled one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Slot s where s.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = { "provider", "provider.user" })
    Optional<Slot> findDetailedById(Long id);

    @EntityGraph(attributePaths = { "provider", "provider.user" })
    @Query("select s from Slot s where s.provider = :provider and s.closed = false and s.startTime >= :from and s.startTime < :to order by s.startTime")
    List<Slot> findOpenFor(@Param("provider") Provider provider, @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @EntityGraph(attributePaths = { "provider", "provider.user" })
    @Query("select s from Slot s where s.closed = false and s.startTime >= :from and s.startTime < :to order by s.startTime")
    List<Slot> findOpenBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select count(s) from Slot s where s.provider = :provider and s.closed = false and s.startTime < :end and s.endTime > :start")
    long countOverlapping(@Param("provider") Provider provider, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
