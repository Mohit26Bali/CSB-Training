package com.hackathon.appointment.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.appointment.dto.AppointmentRequest;
import com.hackathon.appointment.dto.AppointmentResponse;
import com.hackathon.appointment.dto.BulkSlotBody;
import com.hackathon.appointment.dto.DayCount;
import com.hackathon.appointment.dto.ProfileBody;
import com.hackathon.appointment.dto.ProviderDashboard;
import com.hackathon.appointment.dto.ProviderResponse;
import com.hackathon.appointment.dto.SlotBody;
import com.hackathon.appointment.dto.SlotResponse;
import com.hackathon.appointment.entity.Appointment;
import com.hackathon.appointment.entity.AppointmentStatus;
import com.hackathon.appointment.entity.Provider;
import com.hackathon.appointment.entity.Slot;
import com.hackathon.appointment.entity.User;
import com.hackathon.appointment.exception.BusinessRuleException;
import com.hackathon.appointment.exception.ConflictException;
import com.hackathon.appointment.exception.FieldValidationException;
import com.hackathon.appointment.exception.ForbiddenActionException;
import com.hackathon.appointment.exception.InvalidStatusException;
import com.hackathon.appointment.exception.ResourceNotFoundException;
import com.hackathon.appointment.repo.AppointmentRepository;
import com.hackathon.appointment.repo.ProviderRepository;
import com.hackathon.appointment.repo.SlotRepository;
import com.hackathon.appointment.repo.UserRepository;

/**
 * Providers, their slots and the appointments customers make. Every change to a slot's appointment first takes that slot's row lock
 * (under READ_COMMITTED, and before the appointment is loaded), so a slot can never be double-booked; creating slots locks the provider row
 * so two requests cannot create overlapping slots; booking also locks the customer row so they cannot hold two appointments at once.
 */
@Service
public class AppointmentService {

    static final int MAX_DAYS_AHEAD = 60;
    static final int MAX_BULK = 24;
    private static final Set<AppointmentStatus> LIVE = EnumSet.of(AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED);

    private final ProviderRepository providers;
    private final SlotRepository slots;
    private final AppointmentRepository appointments;
    private final UserRepository users;

    public AppointmentService(ProviderRepository providers, SlotRepository slots, AppointmentRepository appointments, UserRepository users) {
        this.providers = providers;
        this.slots = slots;
        this.appointments = appointments;
        this.users = users;
    }

    // ---------- everyone signed in: providers and their free slots ----------

    /** Providers (optionally filtered by text) with the number of free slots in the next 14 days and the first free time. */
    @Transactional(readOnly = true)
    public List<ProviderResponse> providers(String search) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime to = now.plusDays(14);
        Set<Long> taken = appointments.findLiveBetween(LIVE, now, to).stream().map(a -> a.getSlot().getId()).collect(Collectors.toSet());
        Map<Long, List<Slot>> free = slots.findOpenBetween(now, to).stream().filter(s -> !taken.contains(s.getId()))
                .collect(Collectors.groupingBy(s -> s.getProvider().getId()));
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return providers.findAllByOrderByIdAsc().stream()
                .filter(p -> q.isEmpty() || Stream.of(p.getUser().getFullname(), p.getTitle(), p.getLocation(), p.getDescription())
                        .anyMatch(s -> s != null && s.toLowerCase(Locale.ROOT).contains(q)))
                .map(p -> {
                    List<Slot> open = free.getOrDefault(p.getId(), List.of());
                    return ProviderResponse.of(p, open.size(), open.stream().map(Slot::getStartTime).min(Comparator.naturalOrder()).orElse(null));
                }).toList();
    }

    /** One provider's upcoming slots, each marked AVAILABLE or taken. Who took a slot is not shown. */
    @Transactional(readOnly = true)
    public List<SlotResponse> providerSlots(Long providerId, LocalDate from, Integer days) {
        Provider provider = providers.findWithUserById(providerId).orElseThrow(() -> new ResourceNotFoundException("Provider " + providerId + " not found"));
        LocalDateTime now = LocalDateTime.now();
        LocalDate start = from == null || from.isBefore(now.toLocalDate()) ? now.toLocalDate() : from;
        LocalDateTime lower = start.equals(now.toLocalDate()) ? now : start.atStartOfDay();
        return slotsWithAppointments(provider, lower, start.plusDays(range(days)).atStartOfDay(), false);
    }

    private static int range(Integer days) {
        int count = days == null ? 7 : days;
        if (count < 1 || count > 31) {
            throw new FieldValidationException("days", "Ask for between 1 and 31 days");
        }
        return count;
    }

    private List<SlotResponse> slotsWithAppointments(Provider provider, LocalDateTime from, LocalDateTime to, boolean forProvider) {
        Map<Long, Appointment> live = appointments.findForProviderBetween(provider, from, to).stream().filter(a -> LIVE.contains(a.getStatus()))
                .collect(Collectors.toMap(a -> a.getSlot().getId(), a -> a, (a, b) -> a));
        return slots.findOpenFor(provider, from, to).stream().map(s -> SlotResponse.of(s, live.get(s.getId()), forProvider)).toList();
    }

    // ---------- customers ----------

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse book(String username, AppointmentRequest request) {
        Slot slot = slots.findByIdForUpdate(request.slotId()).orElseThrow(() -> new ResourceNotFoundException("Slot " + request.slotId() + " not found"));
        User customer = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
        LocalDateTime now = LocalDateTime.now();
        if (slot.isClosed()) {
            throw new BusinessRuleException("This slot is no longer offered");
        }
        if (!slot.getStartTime().isAfter(now)) {
            throw new FieldValidationException("slotId", "That time has already passed. Choose a later slot");
        }
        if (appointments.existsBySlotAndStatusIn(slot, LIVE)) {
            throw new ConflictException("This slot has just been taken. Please choose another one");
        }
        if (appointments.countOwnOverlapping(customer, LIVE, slot.getStartTime(), slot.getEndTime()) > 0) {
            throw new ConflictException("You already have an appointment at that time");
        }
        Appointment saved = appointments.save(new Appointment(slot, customer, clean(request.reason())));
        return AppointmentResponse.of(saved, now);
    }

    /** Own appointments. scope: upcoming (live and not over), previous (everything else) or all. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<AppointmentResponse> mine(String username, String scope) {
        LocalDateTime now = LocalDateTime.now();
        String wanted = scope == null || scope.isBlank() ? "all" : scope.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "upcoming", "previous").contains(wanted)) {
            throw new FieldValidationException("scope", "Scope must be upcoming, previous or all");
        }
        List<Appointment> own = appointments.findByCustomerOrderBySlotStartTimeAsc(user(username));
        Stream<Appointment> upcoming = own.stream().filter(a -> upcoming(a, now));
        Stream<Appointment> previous = own.stream().filter(a -> !upcoming(a, now)).sorted(Comparator.comparing((Appointment a) -> a.getSlot().getStartTime()).reversed());
        Stream<Appointment> chosen = switch (wanted) {
            case "upcoming" -> upcoming;
            case "previous" -> previous;
            default -> Stream.concat(upcoming, previous);
        };
        return chosen.map(a -> AppointmentResponse.of(a, now)).toList();
    }

    private static boolean upcoming(Appointment a, LocalDateTime now) {
        return LIVE.contains(a.getStatus()) && a.getSlot().getEndTime().isAfter(now);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse cancelOwn(String username, Long id, String reason) {
        AppointmentRepository.Peek peek = appointments.peek(id).orElseThrow(() -> notFound(id));
        if (!peek.getCustomerUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own appointments");
        }
        slots.findByIdForUpdate(peek.getSlotId());
        Appointment appointment = detailed(id);
        requireLive(appointment, "cancelled");
        LocalDateTime now = LocalDateTime.now();
        if (!appointment.getSlot().getStartTime().isAfter(now.plusHours(AppointmentResponse.CANCEL_NOTICE_HOURS))) {
            throw new BusinessRuleException("Appointments can be cancelled up to " + AppointmentResponse.CANCEL_NOTICE_HOURS + " hours before they start. Please contact the provider");
        }
        appointment.cancel(username, clean(reason));
        return AppointmentResponse.of(appointment, now);
    }

    // ---------- providers: slots ----------

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(readOnly = true)
    public List<SlotResponse> mySlots(String username, LocalDate from, Integer days) {
        Provider provider = providerOf(username);
        LocalDate start = from == null ? LocalDate.now() : from;
        return slotsWithAppointments(provider, start.atStartOfDay(), start.plusDays(range(days)).atStartOfDay(), true);
    }

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public SlotResponse createSlot(String username, SlotBody body) {
        Provider provider = lockProvider(username);
        LocalDateTime start = LocalDateTime.of(body.date(), body.startTime());
        Slot slot = newSlot(provider, start, body.minutes());
        return SlotResponse.of(slots.save(slot), null, true);
    }

    /** Cuts the window from fromTime to toTime into consecutive slots of the given length (at most 24). All or nothing. */
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<SlotResponse> createBulk(String username, BulkSlotBody body) {
        Provider provider = lockProvider(username);
        if (!body.toTime().isAfter(body.fromTime())) {
            throw new FieldValidationException("toTime", "End time must be after the start time");
        }
        long window = java.time.Duration.between(body.fromTime(), body.toTime()).toMinutes();
        int count = (int) (window / body.minutes());
        if (count == 0) {
            throw new FieldValidationException("minutes", "The time window is shorter than one slot");
        }
        if (count > MAX_BULK) {
            throw new FieldValidationException("minutes", "That would make " + count + " slots. Create at most " + MAX_BULK + " at once");
        }
        LocalDateTime first = LocalDateTime.of(body.date(), body.fromTime());
        List<Slot> created = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            created.add(newSlot(provider, first.plusMinutes((long) i * body.minutes()), body.minutes()));
        }
        return slots.saveAll(created).stream().map(s -> SlotResponse.of(s, null, true)).toList();
    }

    private Slot newSlot(Provider provider, LocalDateTime start, int minutes) {
        LocalDateTime now = LocalDateTime.now();
        if (!start.isAfter(now)) {
            throw new FieldValidationException("date", "Slots can only be created for a time in the future");
        }
        if (start.toLocalDate().isAfter(now.toLocalDate().plusDays(MAX_DAYS_AHEAD))) {
            throw new FieldValidationException("date", "Slots can be created up to " + MAX_DAYS_AHEAD + " days ahead");
        }
        LocalDateTime end = start.plusMinutes(minutes);
        if (!end.toLocalDate().equals(start.toLocalDate()) && !end.toLocalTime().equals(LocalTime.MIDNIGHT)) {
            throw new FieldValidationException("minutes", "A slot must end on the same day it starts");
        }
        if (slots.countOverlapping(provider, start, end) > 0) {
            throw new ConflictException("You already have a slot that overlaps " + start.toLocalTime() + " to " + end.toLocalTime() + " on " + start.toLocalDate());
        }
        return new Slot(provider, start, end);
    }

    /** Withdraws a slot. A slot that holds a pending or confirmed appointment must have it cancelled first. */
    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void closeSlot(String username, Long id) {
        Slot slot = slots.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Slot " + id + " not found"));
        if (!slot.getProvider().getUser().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only withdraw your own slots");
        }
        if (slot.isClosed()) {
            throw new InvalidStatusException("This slot has already been withdrawn");
        }
        if (appointments.existsBySlotAndStatusIn(slot, LIVE)) {
            throw new ConflictException("This slot has an appointment. Cancel the appointment first");
        }
        slot.close();
    }

    // ---------- providers: appointments ----------

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(readOnly = true)
    public List<AppointmentResponse> appointments(String username, AppointmentStatus status, LocalDate date, String search) {
        LocalDateTime now = LocalDateTime.now();
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return appointments.findAllForProvider(providerOf(username)).stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> date == null || a.getSlot().getStartTime().toLocalDate().equals(date))
                .filter(a -> q.isEmpty() || Stream.of(a.getCustomer().getFullname(), a.getCustomer().getUsername(), a.getReason(), "ap-" + (3000 + a.getId()))
                        .anyMatch(s -> s != null && s.toLowerCase(Locale.ROOT).contains(q)))
                .sorted(Comparator.comparing((Appointment a) -> a.getStatus() == AppointmentStatus.PENDING ? 0 : 1).thenComparing(a -> a.getSlot().getStartTime()))
                .map(a -> AppointmentResponse.of(a, now)).toList();
    }

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse confirm(String username, Long id) {
        lockOwned(username, id);
        Appointment appointment = detailed(id);
        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new InvalidStatusException("Only a pending appointment can be confirmed (this one is " + appointment.getStatus() + ")");
        }
        LocalDateTime now = LocalDateTime.now();
        if (!appointment.getSlot().getStartTime().isAfter(now)) {
            throw new BusinessRuleException("That appointment time has already passed");
        }
        appointment.confirm(username);
        return AppointmentResponse.of(appointment, now);
    }

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AppointmentResponse cancelAsProvider(String username, Long id, String reason) {
        String why = clean(reason);
        if (why == null) {
            throw new FieldValidationException("reason", "Give the customer a reason for the cancellation");
        }
        lockOwned(username, id);
        Appointment appointment = detailed(id);
        requireLive(appointment, "cancelled");
        LocalDateTime now = LocalDateTime.now();
        if (appointment.getSlot().getEndTime().isBefore(now)) {
            throw new BusinessRuleException("A past appointment cannot be cancelled");
        }
        appointment.cancel(username, why);
        return AppointmentResponse.of(appointment, now);
    }

    /** Checks the appointment belongs to the provider's slot, then locks that slot. */
    private void lockOwned(String username, Long id) {
        AppointmentRepository.Peek peek = appointments.peek(id).orElseThrow(() -> notFound(id));
        if (!peek.getProviderUsername().equals(username)) {
            throw new ForbiddenActionException("This appointment is not on your calendar");
        }
        slots.findByIdForUpdate(peek.getSlotId());
    }

    // ---------- providers: profile and dashboard ----------

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional
    public ProviderResponse profile(String username) {
        return ProviderResponse.of(providerOf(username), 0, null);
    }

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional
    public ProviderResponse updateProfile(String username, ProfileBody body) {
        Provider provider = providerOf(username);
        provider.update(body.title().trim(), clean(body.description()), clean(body.location()));
        return ProviderResponse.of(provider, 0, null);
    }

    @PreAuthorize("hasRole('SERVICE_PROVIDER')")
    @Transactional(readOnly = true)
    public ProviderDashboard dashboard(String username) {
        Provider provider = providerOf(username);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        LocalDateTime weekEnd = today.plusDays(7).atStartOfDay();
        List<Appointment> all = appointments.findAllForProvider(provider);
        Set<Long> taken = all.stream().filter(a -> LIVE.contains(a.getStatus())).map(a -> a.getSlot().getId()).collect(Collectors.toSet());
        List<Slot> week = slots.findOpenFor(provider, today.atStartOfDay(), weekEnd);
        List<DayCount> days = IntStream.range(0, 7).mapToObj(i -> {
            LocalDate d = today.plusDays(i);
            List<Slot> onDay = week.stream().filter(s -> s.getStartTime().toLocalDate().equals(d)).toList();
            return new DayCount(d, onDay.size(), (int) onDay.stream().filter(s -> taken.contains(s.getId())).count());
        }).toList();
        int total = week.size();
        int booked = days.stream().mapToInt(DayCount::booked).sum();
        List<Appointment> ahead = all.stream().filter(a -> LIVE.contains(a.getStatus()) && a.getSlot().getStartTime().isAfter(now)).toList();
        return new ProviderDashboard(
                all.stream().filter(a -> LIVE.contains(a.getStatus()) && a.getSlot().getStartTime().toLocalDate().equals(today)).count(),
                ahead.stream().filter(a -> a.getStatus() == AppointmentStatus.PENDING).count(),
                ahead.stream().filter(a -> a.getStatus() == AppointmentStatus.CONFIRMED).count(),
                (int) week.stream().filter(s -> s.getStartTime().isAfter(now) && !taken.contains(s.getId())).count(),
                booked, total == 0 ? 0 : Math.round(booked * 100f / total), days,
                ahead.stream().sorted(Comparator.comparing((Appointment a) -> a.getSlot().getStartTime())).limit(5).map(a -> AppointmentResponse.of(a, now)).toList());
    }

    // ---------- helpers ----------

    private Provider providerOf(String username) {
        return providers.findByUsername(username).orElseGet(() -> createProfile(username));
    }

    private Provider lockProvider(String username) {
        providerOf(username);
        return providers.findByUsernameForUpdate(username).orElseThrow();
    }

    private Provider createProfile(String username) {
        return providers.save(new Provider(user(username), "Service provider", null, null));
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
    }

    private Appointment detailed(Long id) {
        return appointments.findDetailedById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Appointment " + id + " not found");
    }

    private static void requireLive(Appointment appointment, String verb) {
        if (!LIVE.contains(appointment.getStatus())) {
            throw new InvalidStatusException("This appointment is already " + appointment.getStatus() + " and cannot be " + verb);
        }
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
