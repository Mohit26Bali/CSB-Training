package com.hackathon.vehicle.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.vehicle.dto.BookingRequest;
import com.hackathon.vehicle.dto.BookingResponse;
import com.hackathon.vehicle.dto.CompleteBody;
import com.hackathon.vehicle.dto.DayLoad;
import com.hackathon.vehicle.dto.ServiceCount;
import com.hackathon.vehicle.dto.ServiceDashboard;
import com.hackathon.vehicle.dto.ServiceTypeBody;
import com.hackathon.vehicle.dto.ServiceTypeResponse;
import com.hackathon.vehicle.dto.VehicleBody;
import com.hackathon.vehicle.dto.VehicleResponse;
import com.hackathon.vehicle.entity.BookingStatus;
import com.hackathon.vehicle.entity.Center;
import com.hackathon.vehicle.entity.ServiceBooking;
import com.hackathon.vehicle.entity.ServiceType;
import com.hackathon.vehicle.entity.User;
import com.hackathon.vehicle.entity.Vehicle;
import com.hackathon.vehicle.entity.VehicleType;
import com.hackathon.vehicle.exception.BusinessRuleException;
import com.hackathon.vehicle.exception.ConflictException;
import com.hackathon.vehicle.exception.FieldValidationException;
import com.hackathon.vehicle.exception.ForbiddenActionException;
import com.hackathon.vehicle.exception.InvalidStatusException;
import com.hackathon.vehicle.exception.ResourceNotFoundException;
import com.hackathon.vehicle.repo.CenterRepository;
import com.hackathon.vehicle.repo.ServiceBookingRepository;
import com.hackathon.vehicle.repo.ServiceTypeRepository;
import com.hackathon.vehicle.repo.UserRepository;
import com.hackathon.vehicle.repo.VehicleRepository;

/**
 * Vehicles, the service catalogue, bookings and the garage's work. Every change to a booking first takes the service center's
 * row lock (under READ_COMMITTED, and before the booking is loaded), so two customers can never take the last bay and a vehicle
 * can never be booked twice for the same day.
 */
@Service
public class GarageService {

    static final int DEFAULT_BAYS = 5;
    static final int MAX_DAYS_AHEAD = 90;
    private static final Set<BookingStatus> LIVE = EnumSet.of(BookingStatus.BOOKED, BookingStatus.IN_SERVICE);

    private final VehicleRepository vehicles;
    private final ServiceTypeRepository services;
    private final ServiceBookingRepository bookings;
    private final CenterRepository centers;
    private final UserRepository users;

    public GarageService(VehicleRepository vehicles, ServiceTypeRepository services, ServiceBookingRepository bookings,
                         CenterRepository centers, UserRepository users) {
        this.vehicles = vehicles;
        this.services = services;
        this.bookings = bookings;
        this.centers = centers;
        this.users = users;
    }

    // ---------- customer: vehicles ----------

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional
    public VehicleResponse registerVehicle(String username, VehicleBody body) {
        User owner = user(username);
        String number = normalize(body.number());
        if (vehicles.existsByNumber(number)) {
            throw new FieldValidationException("number", "A vehicle with number " + number + " is already registered");
        }
        try {
            Vehicle saved = vehicles.saveAndFlush(new Vehicle(owner, number, body.type(), body.make().trim(), body.model().trim(), body.year()));
            return VehicleResponse.of(saved, 0, null);
        } catch (DataIntegrityViolationException e) {
            throw new FieldValidationException("number", "A vehicle with number " + number + " is already registered");
        }
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<VehicleResponse> myVehicles(String username) {
        return describe(vehicles.findByOwnerOrderByIdDesc(user(username)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteVehicle(String username, Long id) {
        lockCenter();
        Vehicle vehicle = vehicles.findById(id).orElseThrow(() -> new ResourceNotFoundException("Vehicle " + id + " not found"));
        if (!vehicle.getOwner().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only remove your own vehicles");
        }
        if (bookings.existsByVehicle(vehicle)) {
            throw new BusinessRuleException("This vehicle has service bookings, so it cannot be removed");
        }
        vehicles.delete(vehicle);
    }

    /** Finished services of one of the customer's own vehicles, newest first. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<BookingResponse> history(String username, Long vehicleId) {
        Vehicle vehicle = vehicles.findById(vehicleId).orElseThrow(() -> new ResourceNotFoundException("Vehicle " + vehicleId + " not found"));
        if (!vehicle.getOwner().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only see the history of your own vehicles");
        }
        return historyOf(vehicle);
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(readOnly = true)
    public List<BookingResponse> vehicleHistory(Long vehicleId) {
        return historyOf(vehicles.findById(vehicleId).orElseThrow(() -> new ResourceNotFoundException("Vehicle " + vehicleId + " not found")));
    }

    private List<BookingResponse> historyOf(Vehicle vehicle) {
        LocalDate today = LocalDate.now();
        return bookings.findByVehicleOrderByStartDateDesc(vehicle).stream()
                .filter(b -> b.getStatus() == BookingStatus.COMPLETED)
                .map(b -> BookingResponse.of(b, today)).toList();
    }

    // ---------- catalogue and availability ----------

    /** Active services; when a vehicle type is given each one carries its estimated cost for that type. */
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> catalogue(String vehicleType) {
        VehicleType type = vehicleType == null || vehicleType.isBlank() ? VehicleType.CAR : parseType(vehicleType);
        return services.findAllByOrderByNameAsc().stream().filter(ServiceType::isActive)
                .map(s -> ServiceTypeResponse.of(s, s.estimateFor(type))).toList();
    }

    /** Free bays per day for the next days, so a customer can pick a date that is not full. */
    @Transactional(readOnly = true)
    public List<DayLoad> availability(LocalDate from, Integer days) {
        LocalDate start = from == null ? LocalDate.now() : from;
        int count = days == null ? 14 : days;
        if (start.isBefore(LocalDate.now())) {
            throw new FieldValidationException("from", "Start date cannot be in the past");
        }
        if (count < 1 || count > 60) {
            throw new FieldValidationException("days", "Ask for between 1 and 60 days");
        }
        return load(start, count, bays());
    }

    private List<DayLoad> load(LocalDate start, int count, int bays) {
        List<ServiceBooking> live = bookings.findLiveBetween(LIVE, start, start.plusDays(count - 1L));
        return IntStream.range(0, count).mapToObj(i -> {
            LocalDate day = start.plusDays(i);
            int booked = (int) live.stream().filter(b -> b.covers(day)).count();
            return new DayLoad(day, booked, bays, Math.max(0, bays - booked));
        }).toList();
    }

    // ---------- customer: bookings ----------

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse book(String username, BookingRequest request) {
        LocalDate today = LocalDate.now();
        LocalDate date = request.serviceDate();
        if (date.isBefore(today)) {
            throw new FieldValidationException("serviceDate", "Service date cannot be in the past");
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw new FieldValidationException("serviceDate", "Bookings open up to " + MAX_DAYS_AHEAD + " days ahead");
        }
        Center center = lockCenter();
        User customer = user(username);
        Vehicle vehicle = vehicles.findById(request.vehicleId()).orElseThrow(() -> new ResourceNotFoundException("Vehicle " + request.vehicleId() + " not found"));
        if (!vehicle.getOwner().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only book a service for your own vehicle");
        }
        ServiceType type = services.findById(request.serviceTypeId()).orElseThrow(() -> new ResourceNotFoundException("Service " + request.serviceTypeId() + " not found"));
        if (!type.isActive()) {
            throw new BusinessRuleException("That service is not offered at the moment");
        }
        LocalDate end = date.plusDays(type.getDurationDays() - 1L);
        List<ServiceBooking> clash = bookings.findLiveBetween(LIVE, date, end);
        if (clash.stream().anyMatch(b -> b.getVehicle().getId().equals(vehicle.getId()))) {
            throw new ConflictException(vehicle.getNumber() + " already has a service booked that overlaps these dates");
        }
        for (LocalDate day = date; !day.isAfter(end); day = day.plusDays(1)) {
            LocalDate d = day;
            if (clash.stream().filter(b -> b.covers(d)).count() >= center.getBays()) {
                throw new ConflictException("The service center is fully booked on " + d + ". Please choose another date");
            }
        }
        ServiceBooking saved = bookings.save(new ServiceBooking(vehicle, customer, type, date, type.estimateFor(vehicle.getType()), clean(request.notes())));
        return BookingResponse.of(saved, today);
    }

    /** Own bookings. scope: upcoming (booked or in service, not yet over), previous (everything else) or all. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<BookingResponse> mine(String username, String scope) {
        LocalDate today = LocalDate.now();
        String wanted = scope == null || scope.isBlank() ? "all" : scope.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "upcoming", "previous").contains(wanted)) {
            throw new FieldValidationException("scope", "Scope must be upcoming, previous or all");
        }
        List<ServiceBooking> own = bookings.findByCustomerOrderByStartDateDesc(user(username));
        Comparator<ServiceBooking> soonest = Comparator.comparing(ServiceBooking::getStartDate);
        Stream<ServiceBooking> upcoming = own.stream().filter(b -> upcoming(b, today)).sorted(soonest);
        Stream<ServiceBooking> previous = own.stream().filter(b -> !upcoming(b, today));
        Stream<ServiceBooking> chosen = switch (wanted) {
            case "upcoming" -> upcoming;
            case "previous" -> previous;
            default -> Stream.concat(upcoming, previous);
        };
        return chosen.map(b -> BookingResponse.of(b, today)).toList();
    }

    private static boolean upcoming(ServiceBooking b, LocalDate today) {
        return LIVE.contains(b.getStatus()) && !b.getEndDate().isBefore(today);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse cancelOwn(String username, Long id, String reason) {
        ServiceBookingRepository.Peek peek = bookings.peek(id).orElseThrow(() -> notFound(id));
        if (!peek.getCustomerUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own bookings");
        }
        lockCenter();
        ServiceBooking booking = detailed(id);
        requireBooked(booking, "cancelled");
        booking.cancel(username, clean(reason));
        return BookingResponse.of(booking, LocalDate.now());
    }

    // ---------- admin: the schedule ----------

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(readOnly = true)
    public List<BookingResponse> allBookings(BookingStatus status, LocalDate date, String search) {
        LocalDate today = LocalDate.now();
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return bookings.findAllByOrderByStartDateAsc().stream()
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> date == null || b.covers(date))
                .filter(b -> q.isEmpty() || Stream.of(b.getVehicle().getNumber(), b.getVehicle().getMake(), b.getVehicle().getModel(),
                        b.getCustomer().getFullname(), b.getCustomer().getUsername(), b.getServiceType().getName(), "vs-" + (5000 + b.getId()))
                        .anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(q)))
                .sorted(Comparator.comparing((ServiceBooking b) -> rank(b.getStatus())).thenComparing(ServiceBooking::getStartDate))
                .map(b -> BookingResponse.of(b, today)).toList();
    }

    private static int rank(BookingStatus s) {
        return switch (s) {
            case IN_SERVICE -> 0;
            case BOOKED -> 1;
            case COMPLETED -> 2;
            case CANCELLED -> 3;
        };
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse start(String admin, Long id) {
        lockCenter();
        ServiceBooking booking = detailed(id);
        requireBooked(booking, "started");
        if (booking.getStartDate().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Service can start only on or after its service date (" + booking.getStartDate() + ")");
        }
        booking.start(admin);
        return BookingResponse.of(booking, LocalDate.now());
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse complete(String admin, Long id, CompleteBody body) {
        lockCenter();
        ServiceBooking booking = detailed(id);
        if (booking.getStatus() != BookingStatus.IN_SERVICE) {
            throw new InvalidStatusException("Only a vehicle that is in service can be completed (this one is " + booking.getStatus() + ")");
        }
        BigDecimal cost = body.finalCost() == null ? booking.getEstimatedCost() : body.finalCost().setScale(2, java.math.RoundingMode.HALF_UP);
        booking.complete(admin, cost, clean(body.workNotes()));
        return BookingResponse.of(booking, LocalDate.now());
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse cancelAsAdmin(String admin, Long id, String reason) {
        String why = clean(reason);
        if (why == null) {
            throw new FieldValidationException("reason", "Give the customer a reason for the cancellation");
        }
        lockCenter();
        ServiceBooking booking = detailed(id);
        requireBooked(booking, "cancelled");
        booking.cancel(admin, why);
        return BookingResponse.of(booking, LocalDate.now());
    }

    // ---------- admin: vehicles, catalogue, center ----------

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(readOnly = true)
    public List<VehicleResponse> searchVehicles(String search) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        return describe(vehicles.findAllByOrderByNumberAsc().stream()
                .filter(v -> q.isEmpty() || Stream.of(v.getNumber(), v.getMake(), v.getModel(), v.getOwner().getFullname(), v.getOwner().getUsername())
                        .anyMatch(s -> s.toLowerCase(Locale.ROOT).replace(" ", "").contains(q)))
                .toList());
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> allServices() {
        return services.findAllByOrderByNameAsc().stream().map(s -> ServiceTypeResponse.of(s, null)).toList();
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional
    public ServiceTypeResponse createService(ServiceTypeBody body) {
        String name = body.name().trim();
        if (services.existsByNameIgnoreCase(name)) {
            throw new FieldValidationException("name", "A service called " + name + " already exists");
        }
        ServiceType saved = services.save(new ServiceType(name, clean(body.description()), body.durationDays(), body.basePrice(), body.active() == null || body.active()));
        return ServiceTypeResponse.of(saved, null);
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ServiceTypeResponse updateService(Long id, ServiceTypeBody body) {
        lockCenter();
        ServiceType type = services.findById(id).orElseThrow(() -> new ResourceNotFoundException("Service " + id + " not found"));
        String name = body.name().trim();
        if (!type.getName().equalsIgnoreCase(name) && services.existsByNameIgnoreCase(name)) {
            throw new FieldValidationException("name", "A service called " + name + " already exists");
        }
        type.update(name, clean(body.description()), body.durationDays(), body.basePrice(), body.active() == null || body.active());
        return ServiceTypeResponse.of(type, null);
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(readOnly = true)
    public int baysNow() {
        return bays();
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int setBays(int bays) {
        Center center = lockCenter();
        LocalDate today = LocalDate.now();
        int busiest = load(today, 60, bays).stream().mapToInt(DayLoad::booked).max().orElse(0);
        if (bays < busiest) {
            throw new FieldValidationException("bays", "Already " + busiest + " vehicles are booked on one day, so there must be at least that many bays");
        }
        center.setBays(bays);
        return bays;
    }

    @PreAuthorize("hasRole('SERVICE_ADMIN')")
    @Transactional(readOnly = true)
    public ServiceDashboard dashboard() {
        LocalDate today = LocalDate.now();
        YearMonth month = YearMonth.from(today);
        int bays = bays();
        List<ServiceBooking> all = bookings.findAllByOrderByStartDateAsc();
        List<ServiceBooking> done = all.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED
                && YearMonth.from(b.getCompletedAt()).equals(month)).toList();
        List<DayLoad> days = load(today, 14, bays);
        Map<String, Long> byService = all.stream().filter(b -> b.getStatus() != BookingStatus.CANCELLED)
                .collect(Collectors.groupingBy(b -> b.getServiceType().getName(), Collectors.counting()));
        return new ServiceDashboard(bays, days.get(0).booked(),
                all.stream().filter(b -> b.getStatus() == BookingStatus.BOOKED && b.getStartDate().equals(today)).count(),
                all.stream().filter(b -> b.getStatus() == BookingStatus.IN_SERVICE).count(),
                all.stream().filter(b -> b.getStatus() == BookingStatus.BOOKED && !b.getStartDate().isBefore(today)).count(),
                done.size(),
                done.stream().map(ServiceBooking::getFinalCost).reduce(BigDecimal.ZERO, BigDecimal::add),
                byService.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .limit(5).map(e -> new ServiceCount(e.getKey(), e.getValue())).toList(),
                days);
    }

    // ---------- helpers ----------

    private Center lockCenter() {
        return centers.lockCenter().orElseGet(() -> {
            try {
                centers.saveAndFlush(new Center(DEFAULT_BAYS));
            } catch (DataIntegrityViolationException ignored) {
                // another request created it first
            }
            return centers.lockCenter().orElseThrow();
        });
    }

    private int bays() {
        return centers.findById(Center.ID).map(Center::getBays).orElse(DEFAULT_BAYS);
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
    }

    private ServiceBooking detailed(Long id) {
        return bookings.findDetailedById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Booking " + id + " not found");
    }

    private static void requireBooked(ServiceBooking booking, String verb) {
        if (booking.getStatus() != BookingStatus.BOOKED) {
            throw new InvalidStatusException("Only a booking that is still BOOKED can be " + verb + " (this one is " + booking.getStatus() + ")");
        }
    }

    private static VehicleType parseType(String value) {
        try {
            return VehicleType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new FieldValidationException("vehicleType", "Vehicle type must be BIKE, CAR, SUV or VAN");
        }
    }

    private static String normalize(String number) {
        return number.replaceAll("[ -]", "").toUpperCase(Locale.ROOT);
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Adds the number of finished services and the next booked date to each vehicle. */
    private List<VehicleResponse> describe(List<Vehicle> list) {
        LocalDate today = LocalDate.now();
        Map<Long, List<ServiceBooking>> byVehicle = list.isEmpty() ? Map.of() : bookings.findAllByOrderByStartDateAsc().stream()
                .collect(Collectors.groupingBy(b -> b.getVehicle().getId()));
        Function<Vehicle, VehicleResponse> map = v -> {
            List<ServiceBooking> mine = byVehicle.getOrDefault(v.getId(), List.of());
            int completed = (int) mine.stream().filter(b -> b.getStatus() == BookingStatus.COMPLETED).count();
            LocalDate next = mine.stream().filter(b -> b.getStatus() == BookingStatus.BOOKED && !b.getStartDate().isBefore(today))
                    .map(ServiceBooking::getStartDate).min(Comparator.naturalOrder()).orElse(null);
            return VehicleResponse.of(v, completed, next);
        };
        return list.stream().map(map).toList();
    }
}
