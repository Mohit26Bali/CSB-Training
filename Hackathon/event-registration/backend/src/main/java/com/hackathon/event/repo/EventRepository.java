package com.hackathon.event.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.EventStatus;

import jakarta.persistence.LockModeType;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByStatusOrderByStartsAtAsc(EventStatus status);

    List<Event> findAllByOrderByStartsAtDesc();

    /** Row lock: registrations, cancellations and capacity changes for one event are handled one at a time. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}
