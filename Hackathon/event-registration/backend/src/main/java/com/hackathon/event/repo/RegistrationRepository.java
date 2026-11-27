package com.hackathon.event.repo;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.Registration;
import com.hackathon.event.entity.RegistrationStatus;
import com.hackathon.event.entity.User;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /** Plain values only, so the registration itself is not loaded before the event row is locked. */
    interface Peek {
        Long getEventId();

        String getUsername();
    }

    @Query("select r.event.id as eventId, r.user.username as username from Registration r where r.id = :id")
    Optional<Peek> peek(@Param("id") Long id);

    Optional<Registration> findByEventAndUser(Event event, User user);

    long countByEventAndStatus(Event event, RegistrationStatus status);

    Optional<Registration> findFirstByEventAndStatusOrderByRegisteredAtAscIdAsc(Event event, RegistrationStatus status);

    @EntityGraph(attributePaths = { "event", "user" })
    List<Registration> findByUserOrderByRegisteredAtDesc(User user);

    @EntityGraph(attributePaths = { "event", "user" })
    List<Registration> findByStatusIn(Collection<RegistrationStatus> statuses);

    @EntityGraph(attributePaths = { "event", "user" })
    List<Registration> findByEventAndStatusIn(Event event, Collection<RegistrationStatus> statuses);

    @EntityGraph(attributePaths = { "event", "user" })
    List<Registration> findByEventInAndStatus(Collection<Event> events, RegistrationStatus status);
}
