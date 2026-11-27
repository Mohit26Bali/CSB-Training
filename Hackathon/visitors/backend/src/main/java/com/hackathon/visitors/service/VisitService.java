package com.hackathon.visitors.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.visitors.dto.DailyStats;
import com.hackathon.visitors.dto.DayCount;
import com.hackathon.visitors.dto.HostCount;
import com.hackathon.visitors.dto.VisitResponse;
import com.hackathon.visitors.dto.VisitorRequest;
import com.hackathon.visitors.entity.User;
import com.hackathon.visitors.entity.Visit;
import com.hackathon.visitors.entity.VisitStatus;
import com.hackathon.visitors.exception.BusinessRuleException;
import com.hackathon.visitors.exception.ConflictException;
import com.hackathon.visitors.exception.FieldValidationException;
import com.hackathon.visitors.exception.ForbiddenActionException;
import com.hackathon.visitors.exception.InvalidStatusException;
import com.hackathon.visitors.exception.ResourceNotFoundException;
import com.hackathon.visitors.repo.UserRepository;
import com.hackathon.visitors.repo.VisitRepository;

/**
 * Invitations by employees and the gate work done by security. Every change to a visit first takes its row lock (under
 * READ_COMMITTED), so two guards cannot check the same visitor in twice, and a visitor cannot be checked out before check-in.
 */
@Service
public class VisitService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final VisitRepository visits;
    private final UserRepository users;

    public VisitService(VisitRepository visits, UserRepository users) {
        this.visits = visits;
        this.users = users;
    }

    // ---------- employee ----------

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitResponse invite(String username, VisitorRequest request) {
        User host = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        String phone = checked(request);
        if (alreadyInvited(host, phone, request.expectedDate(), null)) {
            throw duplicate(request);
        }
        try {
            Visit saved = visits.saveAndFlush(new Visit(newPassCode(), request.visitorName().trim(), phone, blankToNull(request.company()),
                    request.purpose().trim(), request.expectedDate(), request.expectedTime(), host));
            return VisitResponse.from(saved, LocalDateTime.now());
        } catch (DataIntegrityViolationException ex) { // the pass code collided: practically impossible, but never a 500
            throw new ConflictException("Could not create the pass, please try again");
        }
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(readOnly = true)
    public List<VisitResponse> mine(String username, VisitStatus status, String search) {
        User host = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        LocalDateTime now = LocalDateTime.now();
        return visits.findByHostOrderByExpectedDateDescCreatedAtDesc(host).stream()
                .filter(v -> status == null || v.getStatus() == status)
                .filter(v -> matches(v, search))
                .map(v -> VisitResponse.from(v, now)).collect(Collectors.toList());
    }

    /** Changes the details of an invitation that has not been used yet. */
    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitResponse update(Long id, String username, VisitorRequest request) {
        Visit visit = ownedExpected(id, username, "change");
        String phone = checked(request);
        boolean changedIdentity = !visit.getPhone().equals(phone) || !visit.getExpectedDate().equals(request.expectedDate());
        if (changedIdentity && alreadyInvited(visit.getHost(), phone, request.expectedDate(), visit.getId())) {
            throw duplicate(request);
        }
        visit.update(request.visitorName().trim(), phone, blankToNull(request.company()), request.purpose().trim(), request.expectedDate(), request.expectedTime());
        return VisitResponse.from(visit, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('EMPLOYEE')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitResponse cancel(Long id, String username) {
        Visit visit = ownedExpected(id, username, "cancel");
        visit.cancel();
        return VisitResponse.from(visit, LocalDateTime.now());
    }

    // ---------- security ----------

    /**
     * The gate list. With a date it shows that day (default: today); with a search and no date it looks across all days, so a
     * visitor can be found by name, phone or pass code. Visitors inside come first, then those still expected, by expected time.
     */
    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Transactional(readOnly = true)
    public List<VisitResponse> gateList(LocalDate date, VisitStatus status, String search) {
        LocalDateTime now = LocalDateTime.now();
        boolean searching = search != null && !search.isBlank();
        LocalDate day = date != null ? date : searching ? null : now.toLocalDate();
        return visits.findAllByOrderByExpectedDateDescExpectedTimeAscIdAsc().stream()
                .filter(v -> day == null || v.getExpectedDate().equals(day))
                .filter(v -> status == null || v.getStatus() == status)
                .filter(v -> matches(v, search))
                .sorted(Comparator.comparing((Visit v) -> rank(v.getStatus()))
                        .thenComparing(Visit::getExpectedDate, day == null ? Comparator.reverseOrder() : Comparator.naturalOrder())
                        .thenComparing(v -> v.getExpectedTime() == null ? java.time.LocalTime.MAX : v.getExpectedTime()))
                .map(v -> VisitResponse.from(v, now)).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Transactional(readOnly = true)
    public VisitResponse byPass(String passCode) {
        String code = passCode.trim().toUpperCase(Locale.ROOT);
        return visits.findWithHostByPassCode(code).map(v -> VisitResponse.from(v, LocalDateTime.now()))
                .orElseThrow(() -> new ResourceNotFoundException("No visitor has the pass " + code));
    }

    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitResponse checkIn(Long id, String guard) {
        Visit visit = visits.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        switch (visit.getStatus()) {
            case CHECKED_IN -> throw new InvalidStatusException(visit.getVisitorName() + " is already inside");
            case CHECKED_OUT -> throw new InvalidStatusException(visit.getVisitorName() + " has already left");
            case CANCELLED -> throw new InvalidStatusException("This invitation was cancelled by " + visit.getHost().getFullname());
            default -> { }
        }
        LocalDate today = LocalDate.now();
        if (visit.getExpectedDate().isAfter(today)) {
            throw new BusinessRuleException(visit.getVisitorName() + " is expected on " + visit.getExpectedDate() + ", not today");
        }
        if (visit.getExpectedDate().isBefore(today)) {
            throw new BusinessRuleException("This invitation was for " + visit.getExpectedDate() + ". Ask " + visit.getHost().getFullname() + " to send a new one.");
        }
        visit.checkIn(guard);
        return VisitResponse.from(visit, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public VisitResponse checkOut(Long id, String guard) {
        Visit visit = visits.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        switch (visit.getStatus()) {
            case EXPECTED -> throw new InvalidStatusException(visit.getVisitorName() + " has not been checked in yet, so cannot be checked out");
            case CHECKED_OUT -> throw new InvalidStatusException(visit.getVisitorName() + " has already left");
            case CANCELLED -> throw new InvalidStatusException("This invitation was cancelled");
            default -> { }
        }
        visit.checkOut(guard);
        return VisitResponse.from(visit, LocalDateTime.now());
    }

    @PreAuthorize("hasRole('SECURITY_ADMIN')")
    @Transactional(readOnly = true)
    public DailyStats stats() {
        List<Visit> all = visits.findAllByOrderByExpectedDateDescExpectedTimeAscIdAsc();
        LocalDate today = LocalDate.now();
        List<Visit> todays = all.stream().filter(v -> v.getExpectedDate().equals(today) && v.getStatus() != VisitStatus.CANCELLED).toList();
        double minutes = all.stream().filter(v -> v.getStatus() == VisitStatus.CHECKED_OUT && v.getCheckedInAt() != null && v.getCheckedOutAt() != null)
                .mapToLong(v -> Duration.between(v.getCheckedInAt(), v.getCheckedOutAt()).toMinutes()).average().orElse(0);
        List<DayCount> week = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<Visit> onDay = all.stream().filter(v -> v.getExpectedDate().equals(day) && v.getStatus() != VisitStatus.CANCELLED).toList();
            week.add(new DayCount(day, onDay.size(), onDay.stream().filter(v -> v.getCheckedInAt() != null).count()));
        }
        Map<String, Long> byHost = todays.stream().collect(Collectors.groupingBy(v -> v.getHost().getFullname(), Collectors.counting()));
        List<HostCount> hosts = byHost.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(5).map(e -> new HostCount(e.getKey(), e.getValue())).collect(Collectors.toList());
        return new DailyStats(today, todays.size(),
                todays.stream().filter(v -> v.getStatus() == VisitStatus.EXPECTED).count(),
                all.stream().filter(v -> v.getStatus() == VisitStatus.CHECKED_IN).count(),
                todays.stream().filter(v -> v.getStatus() == VisitStatus.CHECKED_OUT).count(),
                all.stream().filter(v -> v.getExpectedDate().equals(today) && v.getStatus() == VisitStatus.CANCELLED).count(),
                all.stream().filter(v -> v.getExpectedDate().isAfter(today) && !v.getExpectedDate().isAfter(today.plusDays(7)) && v.getStatus() == VisitStatus.EXPECTED).count(),
                all.stream().filter(v -> v.getExpectedDate().equals(today.minusDays(1)) && v.getStatus() == VisitStatus.EXPECTED).count(),
                Math.round(minutes * 10) / 10.0, week, hosts);
    }

    // ---------- helpers ----------

    private Visit ownedExpected(Long id, String username, String verb) {
        Visit visit = visits.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (!visit.getHost().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only " + verb + " your own invitations");
        }
        if (visit.getStatus() != VisitStatus.EXPECTED) {
            throw new InvalidStatusException("Only an invitation that has not been used can be changed; this one is "
                    + visit.getStatus().name().toLowerCase(Locale.ROOT).replace('_', ' '));
        }
        return visit;
    }

    /** Validates the request's date and phone; returns the cleaned phone number. */
    private String checked(VisitorRequest request) {
        if (request.expectedDate().isBefore(LocalDate.now())) {
            throw new FieldValidationException("expectedDate", "The expected date cannot be in the past");
        }
        String phone = request.phone().replaceAll("[\\s()-]", "");
        if (!phone.matches("[+]?[0-9]{7,15}")) {
            throw new FieldValidationException("phone", "Enter a valid phone number");
        }
        return phone;
    }

    /** True if this host already has a live invitation for the same number that day. +91 98450 11201 and 9845011201 are the same number. */
    private boolean alreadyInvited(User host, String phone, LocalDate date, Long ignoreId) {
        String digits = phone.replace("+", "");
        return visits.findByHostAndExpectedDateAndStatusNot(host, date, VisitStatus.CANCELLED).stream()
                .filter(v -> !v.getId().equals(ignoreId))
                .anyMatch(v -> {
                    String other = v.getPhone().replace("+", "");
                    return other.endsWith(digits) || digits.endsWith(other);
                });
    }

    private ConflictException duplicate(VisitorRequest request) {
        return new ConflictException("You have already invited this phone number for " + request.expectedDate());
    }

    private String newPassCode() {
        String code;
        do {
            code = "VP-" + (100000 + RANDOM.nextInt(900000));
        } while (visits.existsByPassCode(code));
        return code;
    }

    private int rank(VisitStatus s) {
        return switch (s) {
            case CHECKED_IN -> 0;
            case EXPECTED -> 1;
            case CHECKED_OUT -> 2;
            case CANCELLED -> 3;
        };
    }

    private boolean matches(Visit v, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String term = search.trim().toLowerCase(Locale.ROOT);
        String digits = term.replaceAll("[^0-9+]", "");
        return v.getVisitorName().toLowerCase(Locale.ROOT).contains(term) || v.getPassCode().toLowerCase(Locale.ROOT).contains(term)
                || (!digits.isEmpty() && v.getPhone().contains(digits))
                || (v.getCompany() != null && v.getCompany().toLowerCase(Locale.ROOT).contains(term))
                || v.getHost().getFullname().toLowerCase(Locale.ROOT).contains(term);
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Visit not found with id " + id);
    }
}
