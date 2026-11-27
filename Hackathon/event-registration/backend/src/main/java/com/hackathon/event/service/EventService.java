package com.hackathon.event.service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.event.dto.EventDashboard;
import com.hackathon.event.dto.EventRequest;
import com.hackathon.event.dto.EventResponse;
import com.hackathon.event.dto.EventStats;
import com.hackathon.event.dto.RegistrationResponse;
import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.EventStatus;
import com.hackathon.event.entity.Registration;
import com.hackathon.event.entity.RegistrationStatus;
import com.hackathon.event.exception.BusinessRuleException;
import com.hackathon.event.exception.FieldValidationException;
import com.hackathon.event.exception.InvalidStatusException;
import com.hackathon.event.exception.ResourceNotFoundException;
import com.hackathon.event.repo.EventRepository;
import com.hackathon.event.repo.RegistrationRepository;

@Service
public class EventService {

    public static final List<String> CATEGORIES = List.of("Tech Talk", "Workshop", "Sports", "Cultural", "Wellness",
            "Town Hall", "Social");

    private final EventRepository events;
    private final RegistrationRepository registrations;
    private final RegistrationService registrationService;

    public EventService(EventRepository events, RegistrationRepository registrations, RegistrationService registrationService) {
        this.events = events;
        this.registrations = registrations;
        this.registrationService = registrationService;
    }

    // ---------- employees (and anyone signed in) ----------

    /** Published events that have not started yet, with seat counts and the caller's own registration. */
    @Transactional(readOnly = true)
    public List<EventResponse> upcoming(String username, String search, String category) {
        LocalDateTime now = LocalDateTime.now();
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        List<Event> matching = events.findByStatusOrderByStartsAtAsc(EventStatus.PUBLISHED).stream()
                .filter(e -> !e.hasStarted(now))
                .filter(e -> category == null || category.isBlank() || e.getCategory().equalsIgnoreCase(category))
                .filter(e -> term.isEmpty() || e.getTitle().toLowerCase(Locale.ROOT).contains(term)
                        || e.getVenue().toLowerCase(Locale.ROOT).contains(term)
                        || e.getDescription().toLowerCase(Locale.ROOT).contains(term))
                .collect(Collectors.toList());
        return toResponses(matching, username, now);
    }

    public List<String> categories() {
        return CATEGORIES;
    }

    // ---------- admin ----------

    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional(readOnly = true)
    public List<EventResponse> listAll(EventStatus status) {
        List<Event> all = events.findAllByOrderByStartsAtDesc().stream()
                .filter(e -> status == null || e.getStatus() == status).collect(Collectors.toList());
        return toResponses(all, null, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional
    public EventResponse create(EventRequest request, String admin) {
        validate(request);
        if (!request.startsAt().isAfter(LocalDateTime.now())) {
            throw new FieldValidationException("startsAt", "A new event must start in the future");
        }
        Event event = events.save(new Event(request.title().trim(), request.category(), request.venue().trim(),
                request.description().trim(), request.startsAt(), request.endsAt(), request.capacity(), admin));
        return EventResponse.from(event, 0, 0, null, null, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventResponse update(Long id, EventRequest request) {
        validate(request);
        Event event = lock(id);
        LocalDateTime now = LocalDateTime.now();
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidStatusException("A cancelled event cannot be edited");
        }
        if (event.hasStarted(now)) {
            throw new BusinessRuleException("This event has already started and can no longer be edited");
        }
        if (!request.startsAt().isAfter(now)) {
            throw new FieldValidationException("startsAt", "The event must start in the future");
        }
        long confirmed = registrations.countByEventAndStatus(event, RegistrationStatus.CONFIRMED);
        if (request.capacity() < confirmed) {
            throw new FieldValidationException("capacity", confirmed + " people are already confirmed, so the capacity cannot be below " + confirmed);
        }
        event.update(request.title().trim(), request.category(), request.venue().trim(), request.description().trim(),
                request.startsAt(), request.endsAt(), request.capacity());
        registrationService.promoteWaitlist(event); // a bigger capacity frees seats for people waiting
        return toResponses(List.of(event), null, now).get(0);
    }

    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventResponse publish(Long id) {
        Event event = lock(id);
        if (event.getStatus() != EventStatus.DRAFT) {
            throw new InvalidStatusException("Only a draft can be published; this event is " + event.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (event.hasStarted(LocalDateTime.now())) {
            throw new BusinessRuleException("This event's start time has passed, so it cannot be published");
        }
        event.publish();
        return toResponses(List.of(event), null, LocalDateTime.now()).get(0);
    }

    /** Cancels the event and every live registration on it. */
    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public EventResponse cancel(Long id) {
        Event event = lock(id);
        if (event.getStatus() == EventStatus.CANCELLED) {
            throw new InvalidStatusException("This event is already cancelled");
        }
        if (event.hasStarted(LocalDateTime.now())) {
            throw new BusinessRuleException("This event has already started and cannot be cancelled");
        }
        event.cancel();
        registrations.findByEventAndStatusIn(event, RegistrationStatus.ACTIVE)
                .forEach(r -> r.setStatus(RegistrationStatus.CANCELLED));
        return toResponses(List.of(event), null, LocalDateTime.now()).get(0);
    }

    /** Confirmed participants first, then the waitlist in order. */
    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional(readOnly = true)
    public List<RegistrationResponse> participants(Long id) {
        Event event = events.findById(id).orElseThrow(() -> new ResourceNotFoundException("Event not found with id " + id));
        List<Registration> active = registrations.findByEventAndStatusIn(event, RegistrationStatus.ACTIVE);
        Map<Long, Integer> positions = registrationService.positions(
                active.stream().filter(r -> r.getStatus() == RegistrationStatus.WAITLISTED).collect(Collectors.toList()));
        return active.stream()
                .sorted(Comparator.comparing(Registration::getStatus).thenComparing(Registration::getRegisteredAt).thenComparing(Registration::getId))
                .map(r -> RegistrationResponse.from(r, positions.get(r.getId())))
                .collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('EVENT_ADMIN')")
    @Transactional(readOnly = true)
    public EventDashboard dashboard() {
        LocalDateTime now = LocalDateTime.now();
        List<Event> all = events.findAllByOrderByStartsAtDesc();
        Map<Long, Map<RegistrationStatus, Long>> counts = countsByEvent();
        List<EventStats> stats = all.stream().map(e -> {
            long confirmed = counts.getOrDefault(e.getId(), Map.of()).getOrDefault(RegistrationStatus.CONFIRMED, 0L);
            long waitlisted = counts.getOrDefault(e.getId(), Map.of()).getOrDefault(RegistrationStatus.WAITLISTED, 0L);
            return new EventStats(e.getId(), e.getTitle(), e.getCategory(), e.getStartsAt(), e.getStatus().name(),
                    e.getCapacity(), confirmed, waitlisted, (int) Math.round(100.0 * confirmed / e.getCapacity()));
        }).collect(Collectors.toList());
        long upcoming = all.stream().filter(e -> e.getStatus() == EventStatus.PUBLISHED && !e.hasStarted(now)).count();
        long confirmedSeats = stats.stream().mapToLong(EventStats::confirmed).sum();
        long waitlisted = stats.stream().mapToLong(EventStats::waitlisted).sum();
        return new EventDashboard(all.size(), upcoming, confirmedSeats, waitlisted, stats);
    }

    // ---------- helpers ----------

    private Event lock(Long id) {
        return events.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Event not found with id " + id));
    }

    private void validate(EventRequest request) {
        if (CATEGORIES.stream().noneMatch(c -> c.equals(request.category()))) {
            throw new FieldValidationException("category", "Choose one of: " + String.join(", ", CATEGORIES));
        }
        if (!request.endsAt().isAfter(request.startsAt())) {
            throw new FieldValidationException("endsAt", "The event must end after it starts");
        }
    }

    private Map<Long, Map<RegistrationStatus, Long>> countsByEvent() {
        return registrations.findByStatusIn(RegistrationStatus.ACTIVE).stream().collect(Collectors.groupingBy(
                r -> r.getEvent().getId(), Collectors.groupingBy(Registration::getStatus, Collectors.counting())));
    }

    private List<EventResponse> toResponses(List<Event> list, String username, LocalDateTime now) {
        Map<Long, Map<RegistrationStatus, Long>> counts = countsByEvent();
        Map<Long, Registration> mine = username == null ? Map.of()
                : registrations.findByStatusIn(RegistrationStatus.ACTIVE).stream()
                        .filter(r -> r.getUser().getUsername().equals(username))
                        .collect(Collectors.toMap(r -> r.getEvent().getId(), r -> r));
        return list.stream().map(e -> {
            Map<RegistrationStatus, Long> byStatus = counts.getOrDefault(e.getId(), Map.of());
            Registration own = mine.get(e.getId());
            return EventResponse.from(e, byStatus.getOrDefault(RegistrationStatus.CONFIRMED, 0L),
                    byStatus.getOrDefault(RegistrationStatus.WAITLISTED, 0L),
                    own == null ? null : own.getStatus().name(), own == null ? null : own.getId(), now);
        }).collect(Collectors.toList());
    }
}
