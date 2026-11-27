package com.hackathon.event.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.event.dto.RegistrationRequest;
import com.hackathon.event.dto.RegistrationResponse;
import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.EventStatus;
import com.hackathon.event.entity.Registration;
import com.hackathon.event.entity.RegistrationStatus;
import com.hackathon.event.entity.User;
import com.hackathon.event.exception.BusinessRuleException;
import com.hackathon.event.exception.ConflictException;
import com.hackathon.event.exception.FieldValidationException;
import com.hackathon.event.exception.ForbiddenActionException;
import com.hackathon.event.exception.ResourceNotFoundException;
import com.hackathon.event.repo.EventRepository;
import com.hackathon.event.repo.RegistrationRepository;
import com.hackathon.event.repo.UserRepository;

/**
 * Registrations, capacity and the waitlist. Every change to an event's seats first locks that event's row
 * (under READ_COMMITTED), so two people can never take the last seat.
 */
@Service
public class RegistrationService {

    private final RegistrationRepository registrations;
    private final EventRepository events;
    private final UserRepository users;

    public RegistrationService(RegistrationRepository registrations, EventRepository events, UserRepository users) {
        this.registrations = registrations;
        this.events = events;
        this.users = users;
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationResponse register(String username, RegistrationRequest request) {
        Event event = events.findByIdForUpdate(request.eventId())
                .orElseThrow(() -> new FieldValidationException("eventId", "That event does not exist"));
        User user = findUser(username);
        LocalDateTime now = LocalDateTime.now();
        if (event.getStatus() != EventStatus.PUBLISHED) {
            throw new BusinessRuleException("'" + event.getTitle() + "' is not open for registration");
        }
        if (event.hasStarted(now)) {
            throw new BusinessRuleException("'" + event.getTitle() + "' has already started, so registration is closed");
        }
        Registration existing = registrations.findByEventAndUser(event, user).orElse(null);
        if (existing != null && existing.isActive()) {
            throw duplicate(event, existing);
        }
        boolean full = registrations.countByEventAndStatus(event, RegistrationStatus.CONFIRMED) >= event.getCapacity();
        if (full && !Boolean.TRUE.equals(request.joinWaitlist())) {
            throw new ConflictException("'" + event.getTitle() + "' is full. You can join the waitlist instead.");
        }
        RegistrationStatus status = full ? RegistrationStatus.WAITLISTED : RegistrationStatus.CONFIRMED;
        try {
            Registration saved;
            if (existing != null) { // was cancelled earlier: re-activate the same row
                existing.reactivate(status);
                saved = registrations.saveAndFlush(existing);
            } else {
                saved = registrations.saveAndFlush(new Registration(event, user, status));
            }
            return RegistrationResponse.from(saved, status == RegistrationStatus.WAITLISTED ? waitlistPosition(event, saved) : null);
        } catch (DataIntegrityViolationException ex) { // the same person registered at the same moment
            throw new ConflictException("You are already registered for '" + event.getTitle() + "'");
        }
    }

    /** Cancels the caller's own registration; if a seat was freed the first person on the waitlist gets it. */
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegistrationResponse cancel(String username, Long registrationId) {
        RegistrationRepository.Peek peek = registrations.peek(registrationId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration not found with id " + registrationId));
        if (!peek.getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own registration");
        }
        Event event = events.findByIdForUpdate(peek.getEventId()).orElseThrow();
        Registration registration = registrations.findById(registrationId).orElseThrow();
        if (!registration.isActive()) {
            throw new ConflictException("This registration is already cancelled");
        }
        if (event.hasStarted(LocalDateTime.now())) {
            throw new BusinessRuleException("'" + event.getTitle() + "' has already started, so you can no longer cancel");
        }
        boolean freedSeat = registration.getStatus() == RegistrationStatus.CONFIRMED;
        registration.setStatus(RegistrationStatus.CANCELLED);
        registrations.flush();
        if (freedSeat) {
            promoteWaitlist(event);
        }
        return RegistrationResponse.from(registration, null);
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<RegistrationResponse> mine(String username) {
        User user = findUser(username);
        List<Registration> own = registrations.findByUserOrderByRegisteredAtDesc(user).stream()
                .filter(Registration::isActive).collect(Collectors.toList());
        List<Event> waitlistEvents = own.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED)
                .map(Registration::getEvent).distinct().collect(Collectors.toList());
        Map<Long, List<Registration>> queues = waitlistEvents.isEmpty() ? Map.of()
                : registrations.findByEventInAndStatus(waitlistEvents, RegistrationStatus.WAITLISTED).stream()
                        .collect(Collectors.groupingBy(r -> r.getEvent().getId()));
        return own.stream().sorted(Comparator.comparing((Registration r) -> r.getEvent().getStartsAt()))
                .map(r -> RegistrationResponse.from(r, r.getStatus() == RegistrationStatus.WAITLISTED
                        ? position(queues.getOrDefault(r.getEvent().getId(), List.of()), r) : null))
                .collect(Collectors.toList());
    }

    /** Fills free seats from the waitlist, oldest first. Callers must already hold the event's row lock. */
    void promoteWaitlist(Event event) {
        long confirmed = registrations.countByEventAndStatus(event, RegistrationStatus.CONFIRMED);
        while (confirmed < event.getCapacity()) {
            Registration next = registrations
                    .findFirstByEventAndStatusOrderByRegisteredAtAscIdAsc(event, RegistrationStatus.WAITLISTED).orElse(null);
            if (next == null) {
                return;
            }
            next.setStatus(RegistrationStatus.CONFIRMED);
            registrations.saveAndFlush(next);
            confirmed++;
        }
    }

    // ---------- helpers ----------

    private User findUser(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
    }

    private ConflictException duplicate(Event event, Registration existing) {
        return new ConflictException("You are already " + (existing.getStatus() == RegistrationStatus.CONFIRMED
                ? "registered for" : "on the waitlist for") + " '" + event.getTitle() + "'");
    }

    private int waitlistPosition(Event event, Registration registration) {
        return position(registrations.findByEventInAndStatus(Set.of(event), RegistrationStatus.WAITLISTED), registration);
    }

    private int position(List<Registration> queue, Registration registration) {
        List<Long> order = queue.stream()
                .sorted(Comparator.comparing(Registration::getRegisteredAt).thenComparing(Registration::getId))
                .map(Registration::getId).collect(Collectors.toList());
        return order.indexOf(registration.getId()) + 1;
    }

    /** For the admin's participant list. */
    Map<Long, Integer> positions(List<Registration> waitlisted) {
        Map<Long, Integer> result = new HashMap<>();
        List<Registration> sorted = waitlisted.stream()
                .sorted(Comparator.comparing(Registration::getRegisteredAt).thenComparing(Registration::getId))
                .collect(Collectors.toList());
        for (int i = 0; i < sorted.size(); i++) {
            result.put(sorted.get(i).getId(), i + 1);
        }
        return result;
    }
}
