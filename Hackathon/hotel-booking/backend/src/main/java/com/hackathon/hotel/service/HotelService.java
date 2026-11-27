package com.hackathon.hotel.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.hotel.dto.BookingRequest;
import com.hackathon.hotel.dto.BookingResponse;
import com.hackathon.hotel.dto.CalendarResponse;
import com.hackathon.hotel.dto.HotelDashboard;
import com.hackathon.hotel.dto.RoomBody;
import com.hackathon.hotel.dto.RoomCalendar;
import com.hackathon.hotel.dto.RoomResponse;
import com.hackathon.hotel.dto.Span;
import com.hackathon.hotel.dto.TypeStats;
import com.hackathon.hotel.entity.Booking;
import com.hackathon.hotel.entity.BookingStatus;
import com.hackathon.hotel.entity.Room;
import com.hackathon.hotel.entity.RoomType;
import com.hackathon.hotel.entity.User;
import com.hackathon.hotel.exception.BusinessRuleException;
import com.hackathon.hotel.exception.ConflictException;
import com.hackathon.hotel.exception.FieldValidationException;
import com.hackathon.hotel.exception.ForbiddenActionException;
import com.hackathon.hotel.exception.InvalidStatusException;
import com.hackathon.hotel.exception.ResourceNotFoundException;
import com.hackathon.hotel.repo.BookingRepository;
import com.hackathon.hotel.repo.RoomRepository;
import com.hackathon.hotel.repo.UserRepository;

/**
 * Rooms, availability, bookings and the admin's work. Every change that affects a room's calendar first takes that room's row
 * lock (under READ_COMMITTED), so two confirmations can never put two guests in the same room on the same night.
 */
@Service
public class HotelService {

    static final int MAX_NIGHTS = 30;
    private static final Set<BookingStatus> LIVE = EnumSet.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final UserRepository users;

    public HotelService(RoomRepository rooms, BookingRepository bookings, UserRepository users) {
        this.rooms = rooms;
        this.bookings = bookings;
        this.users = users;
    }

    // ---------- guests: search and book ----------

    /** Active rooms that fit the party and have no confirmed booking in [checkIn, checkOut), each priced for the whole stay. */
    @Transactional(readOnly = true)
    public List<RoomResponse> available(LocalDate checkIn, LocalDate checkOut, String type, Integer guests) {
        int nights = validateStay(checkIn, checkOut);
        RoomType wanted = type == null || type.isBlank() ? null : parseType(type);
        Set<Long> taken = bookings.findAllOverlapping(BookingStatus.CONFIRMED, checkIn, checkOut).stream()
                .map(b -> b.getRoom().getId()).collect(Collectors.toSet());
        return rooms.findAllByOrderByFloorAscNumberAsc().stream()
                .filter(Room::isActive)
                .filter(r -> wanted == null || r.getType() == wanted)
                .filter(r -> guests == null || r.getCapacity() >= guests)
                .filter(r -> !taken.contains(r.getId()))
                .map(r -> RoomResponse.from(r, nights)).collect(Collectors.toList());
    }

    public List<String> types() {
        return Arrays.stream(RoomType.values()).map(Enum::name).toList();
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse book(String username, BookingRequest request) {
        Room room = rooms.findByIdForUpdate(request.roomId()).orElseThrow(() -> new FieldValidationException("roomId", "That room does not exist"));
        User customer = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        int nights = validateStay(request.checkIn(), request.checkOut());
        if (!room.isActive()) {
            throw new BusinessRuleException("Room " + room.getNumber() + " is not open for booking");
        }
        if (request.guests() > room.getCapacity()) {
            throw new FieldValidationException("guests", "Room " + room.getNumber() + " sleeps at most " + room.getCapacity());
        }
        if (!bookings.findOverlapping(room, EnumSet.of(BookingStatus.CONFIRMED), request.checkIn(), request.checkOut()).isEmpty()) {
            throw new ConflictException("Room " + room.getNumber() + " is not available for those dates");
        }
        if (bookings.countOwnOverlapping(room, customer, LIVE, request.checkIn(), request.checkOut()) > 0) {
            throw new ConflictException("You already have a booking for room " + room.getNumber() + " on those dates");
        }
        String requests = request.specialRequests() == null || request.specialRequests().isBlank() ? null : request.specialRequests().trim();
        Booking saved = bookings.save(new Booking(room, customer, request.checkIn(), request.checkOut(), request.guests(),
                room.getPricePerNight().multiply(BigDecimal.valueOf(nights)), nights, requests));
        return BookingResponse.from(saved, LocalDate.now(), 0);
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<BookingResponse> mine(String username, BookingStatus status) {
        User customer = users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User not found: " + username));
        LocalDate today = LocalDate.now();
        return bookings.findByCustomerOrderByCheckInDesc(customer).stream()
                .filter(b -> status == null || b.getStatus() == status)
                .map(b -> BookingResponse.from(b, today, 0)).collect(Collectors.toList());
    }

    /** A guest cancels their own pending or confirmed booking, up to the day before check-in. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse cancelOwn(String username, Long id, String reason) {
        BookingRepository.Peek peek = bookings.peek(id).orElseThrow(() -> notFound(id));
        if (!peek.getCustomerUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own bookings");
        }
        rooms.findByIdForUpdate(peek.getRoomId());
        Booking booking = bookings.findWithRoomById(id).orElseThrow();
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidStatusException("This booking is already cancelled");
        }
        if (!booking.getCheckIn().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("Bookings can only be cancelled before the check-in date. Please contact the hotel.");
        }
        booking.cancel(username, blankToNull(reason));
        return BookingResponse.from(booking, LocalDate.now(), 0);
    }

    // ---------- admin: bookings ----------

    /** Pending requests first (soonest check-in on top), then everything else by check-in date, newest stays first. */
    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(readOnly = true)
    public List<BookingResponse> allBookings(BookingStatus status, Long roomId, String search) {
        LocalDate today = LocalDate.now();
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return bookings.findAllByOrderByCheckInAsc().stream()
                .filter(b -> status == null || b.getStatus() == status)
                .filter(b -> roomId == null || b.getRoom().getId().equals(roomId))
                .filter(b -> term.isEmpty() || b.getCustomer().getFullname().toLowerCase(Locale.ROOT).contains(term)
                        || b.getRoom().getNumber().toLowerCase(Locale.ROOT).contains(term)
                        || BookingResponse.numberOf(b.getId()).toLowerCase(Locale.ROOT).contains(term))
                .sorted(Comparator.comparing((Booking b) -> b.getStatus() != BookingStatus.PENDING)
                        .thenComparing(b -> b.getStatus() == BookingStatus.PENDING ? b.getCheckIn() : LocalDate.MAX)
                        .thenComparing(Booking::getCheckIn, Comparator.reverseOrder()))
                .map(b -> BookingResponse.from(b, today, 0)).collect(Collectors.toList());
    }

    /** Confirms a pending booking; any other pending requests for overlapping dates in the same room are released. */
    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse confirm(Long id, String admin) {
        BookingRepository.Peek peek = bookings.peek(id).orElseThrow(() -> notFound(id));
        Room room = rooms.findByIdForUpdate(peek.getRoomId()).orElseThrow();
        Booking booking = bookings.findWithRoomById(id).orElseThrow(); // loaded only after the room is locked
        if (booking.getStatus() != BookingStatus.PENDING) {
            throw new InvalidStatusException("Only a pending booking can be confirmed; this one is " + booking.getStatus().name().toLowerCase(Locale.ROOT));
        }
        if (!booking.getCheckOut().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("This stay is already over, so it cannot be confirmed");
        }
        if (!room.isActive()) {
            throw new BusinessRuleException("Room " + room.getNumber() + " is closed, so it cannot be confirmed");
        }
        if (!bookings.findOverlapping(room, EnumSet.of(BookingStatus.CONFIRMED), booking.getCheckIn(), booking.getCheckOut()).isEmpty()) {
            throw new ConflictException("Room " + room.getNumber() + " is already confirmed for an overlapping stay");
        }
        booking.confirm(admin);
        List<Booking> released = bookings.findOverlapping(room, EnumSet.of(BookingStatus.PENDING), booking.getCheckIn(), booking.getCheckOut()).stream()
                .filter(other -> !other.getId().equals(booking.getId())).toList();
        released.forEach(other -> other.cancel("system", "The room was confirmed for another guest on those dates"));
        return BookingResponse.from(booking, LocalDate.now(), released.size());
    }

    /** The admin cancels a pending or confirmed booking; a reason is required and the guest sees it. */
    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BookingResponse adminCancel(Long id, String reason, String admin) {
        BookingRepository.Peek peek = bookings.peek(id).orElseThrow(() -> notFound(id));
        rooms.findByIdForUpdate(peek.getRoomId());
        Booking booking = bookings.findWithRoomById(id).orElseThrow();
        if (booking.getStatus() == BookingStatus.CANCELLED) {
            throw new InvalidStatusException("This booking is already cancelled");
        }
        if (!booking.getCheckOut().isAfter(LocalDate.now())) {
            throw new BusinessRuleException("This stay is already over, so it cannot be cancelled");
        }
        String text = blankToNull(reason);
        if (text == null) {
            throw new FieldValidationException("reason", "A reason is required when the hotel cancels a booking");
        }
        booking.cancel(admin, text);
        return BookingResponse.from(booking, LocalDate.now(), 0);
    }

    // ---------- admin: rooms ----------

    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(readOnly = true)
    public List<RoomResponse> allRooms(String type) {
        RoomType wanted = type == null || type.isBlank() ? null : parseType(type);
        return rooms.findAllByOrderByFloorAscNumberAsc().stream().filter(r -> wanted == null || r.getType() == wanted)
                .map(r -> RoomResponse.from(r, null)).collect(Collectors.toList());
    }

    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional
    public RoomResponse createRoom(RoomBody body) {
        String number = normalise(body.number());
        if (rooms.existsByNumber(number)) {
            throw duplicate(number);
        }
        try {
            Room saved = rooms.saveAndFlush(new Room(number, parseType(body.type()), body.floor(), money(body.pricePerNight()), body.capacity(),
                    blankToNull(body.description()), blankToNull(body.amenities())));
            return RoomResponse.from(saved, null);
        } catch (DataIntegrityViolationException ex) { // created at the same moment
            throw duplicate(number);
        }
    }

    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RoomResponse updateRoom(Long id, RoomBody body) {
        Room room = rooms.findByIdForUpdate(id).orElseThrow(() -> new ResourceNotFoundException("Room not found with id " + id));
        String number = normalise(body.number());
        if (!room.getNumber().equals(number) && rooms.existsByNumber(number)) {
            throw duplicate(number);
        }
        boolean active = body.active() == null || body.active();
        if (!active && room.isActive() && bookings.existsByRoomAndStatusInAndCheckOutAfter(room, LIVE, LocalDate.now())) {
            throw new BusinessRuleException("Room " + room.getNumber() + " still has upcoming or current bookings. Cancel or move them before closing the room.");
        }
        room.update(number, parseType(body.type()), body.floor(), money(body.pricePerNight()), body.capacity(), blankToNull(body.description()),
                blankToNull(body.amenities()), active);
        return RoomResponse.from(room, null);
    }

    // ---------- admin: calendar and dashboard ----------

    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(readOnly = true)
    public CalendarResponse calendar(YearMonth month) {
        LocalDate first = month.atDay(1);
        LocalDate end = month.plusMonths(1).atDay(1);
        Map<Long, List<Booking>> byRoom = bookings.findAllByOrderByCheckInAsc().stream()
                .filter(b -> LIVE.contains(b.getStatus()) && b.overlaps(first, end))
                .collect(Collectors.groupingBy(b -> b.getRoom().getId()));
        List<RoomCalendar> list = rooms.findAllByOrderByFloorAscNumberAsc().stream().map(r -> new RoomCalendar(r.getId(), r.getNumber(), r.getType().name(),
                r.isActive(), byRoom.getOrDefault(r.getId(), List.of()).stream().map(b -> new Span(b.getId(), BookingResponse.numberOf(b.getId()),
                        b.getStatus().name(), b.getCheckIn(), b.getCheckOut(), b.getCustomer().getFullname())).collect(Collectors.toList()))).collect(Collectors.toList());
        return new CalendarResponse(month.toString(), first, month.lengthOfMonth(), list);
    }

    @PreAuthorize("hasRole('HOTEL_ADMIN')")
    @Transactional(readOnly = true)
    public HotelDashboard dashboard() {
        LocalDate today = LocalDate.now();
        List<Room> all = rooms.findAllByOrderByFloorAscNumberAsc();
        List<Booking> every = bookings.findAllByOrderByCheckInAsc();
        List<Booking> confirmed = every.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).toList();
        Set<Long> occupied = confirmed.stream().filter(b -> !b.getCheckIn().isAfter(today) && b.getCheckOut().isAfter(today))
                .map(b -> b.getRoom().getId()).collect(Collectors.toSet());
        List<Room> active = all.stream().filter(Room::isActive).toList();
        YearMonth thisMonth = YearMonth.from(today);
        BigDecimal revenue = confirmed.stream().filter(b -> YearMonth.from(b.getCheckIn()).equals(thisMonth))
                .map(Booking::getTotalPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<RoomType, List<Room>> byType = all.stream().collect(Collectors.groupingBy(Room::getType));
        List<TypeStats> types = Arrays.stream(RoomType.values()).filter(byType::containsKey).map(t -> {
            List<Room> list = byType.get(t);
            BigDecimal avg = list.stream().map(Room::getPricePerNight).reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(list.size()), 0, RoundingMode.HALF_UP);
            return new TypeStats(t.name(), list.size(), (int) list.stream().filter(r -> occupied.contains(r.getId())).count(), avg);
        }).collect(Collectors.toList());
        List<BookingResponse> next = confirmed.stream().filter(b -> !b.getCheckIn().isBefore(today)).limit(5)
                .map(b -> BookingResponse.from(b, today, 0)).collect(Collectors.toList());
        return new HotelDashboard(all.size(), active.size(), occupied.size(), active.isEmpty() ? 0 : (int) Math.round(100.0 * occupied.size() / active.size()),
                (int) confirmed.stream().filter(b -> b.getCheckIn().equals(today)).count(), (int) confirmed.stream().filter(b -> b.getCheckOut().equals(today)).count(),
                every.stream().filter(b -> b.getStatus() == BookingStatus.PENDING).count(), revenue, types, next);
    }

    // ---------- helpers ----------

    /** Checks the dates and returns the number of nights. */
    int validateStay(LocalDate checkIn, LocalDate checkOut) {
        if (checkIn == null) {
            throw new FieldValidationException("checkIn", "Check-in date is required");
        }
        if (checkOut == null) {
            throw new FieldValidationException("checkOut", "Check-out date is required");
        }
        if (checkIn.isBefore(LocalDate.now())) {
            throw new FieldValidationException("checkIn", "Check-in cannot be in the past");
        }
        if (!checkOut.isAfter(checkIn)) {
            throw new FieldValidationException("checkOut", "Check-out must be after check-in");
        }
        long nights = ChronoUnit.DAYS.between(checkIn, checkOut);
        if (nights > MAX_NIGHTS) {
            throw new FieldValidationException("checkOut", "A single booking can be at most " + MAX_NIGHTS + " nights");
        }
        return (int) nights;
    }

    private RoomType parseType(String value) {
        try {
            return RoomType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new FieldValidationException("type", "Choose one of: standard, deluxe, suite, family");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String normalise(String number) {
        return number.trim().toUpperCase(Locale.ROOT);
    }

    private FieldValidationException duplicate(String number) {
        return new FieldValidationException("number", "Room " + number + " already exists");
    }

    private ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Booking not found with id " + id);
    }

    private String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
