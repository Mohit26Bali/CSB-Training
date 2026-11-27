package com.hackathon.hotel.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.hotel.entity.Booking;
import com.hackathon.hotel.entity.BookingStatus;
import com.hackathon.hotel.entity.Role;
import com.hackathon.hotel.entity.Room;
import com.hackathon.hotel.entity.RoomType;
import com.hackathon.hotel.entity.User;
import com.hackathon.hotel.repo.BookingRepository;
import com.hackathon.hotel.repo.RoomRepository;
import com.hackathon.hotel.repo.UserRepository;

/**
 * Seeds two admins, 30 guests, 18 rooms (one closed for renovation) and about 70 bookings: three months of past stays, guests in the
 * hotel tonight, confirmed stays ahead, pending requests (two of them for the same room and dates, to show the release on confirmation)
 * and a few cancellations. Confirmed stays never overlap in a room. aarav.sharma has an upcoming confirmed stay, a pending request,
 * a past stay and a cancelled booking. Idempotent: users only when missing, rooms and bookings only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "hoteladmin", "Meera Kapoor" },
            new String[] { "reception", "Suresh Pillai" });

    private static final List<String[]> GUESTS = List.of(
            new String[] { "aarav.sharma", "Aarav Sharma" }, new String[] { "diya.patel", "Diya Patel" },
            new String[] { "rohan.mehta", "Rohan Mehta" }, new String[] { "ananya.iyer", "Ananya Iyer" },
            new String[] { "vivaan.gupta", "Vivaan Gupta" }, new String[] { "kavya.nair", "Kavya Nair" },
            new String[] { "arjun.reddy", "Arjun Reddy" }, new String[] { "meera.joshi", "Meera Joshi" },
            new String[] { "ishaan.verma", "Ishaan Verma" }, new String[] { "saanvi.rao", "Saanvi Rao" },
            new String[] { "aditya.singh", "Aditya Singh" }, new String[] { "karan.malhotra", "Karan Malhotra" },
            new String[] { "neha.kapoor", "Neha Kapoor" }, new String[] { "siddharth.jain", "Siddharth Jain" },
            new String[] { "pooja.bhatt", "Pooja Bhatt" }, new String[] { "manish.kulkarni", "Manish Kulkarni" },
            new String[] { "tanvi.desai", "Tanvi Desai" }, new String[] { "harsh.vora", "Harsh Vora" },
            new String[] { "ritika.sen", "Ritika Sen" }, new String[] { "varun.chopra", "Varun Chopra" },
            new String[] { "shreya.menon", "Shreya Menon" }, new String[] { "nikhil.pillai", "Nikhil Pillai" },
            new String[] { "aditi.rao", "Aditi Rao" }, new String[] { "gaurav.bhatia", "Gaurav Bhatia" },
            new String[] { "swati.mishra", "Swati Mishra" }, new String[] { "rajesh.nambiar", "Rajesh Nambiar" },
            new String[] { "isha.agarwal", "Isha Agarwal" }, new String[] { "pranav.joshi", "Pranav Joshi" },
            new String[] { "lakshmi.venkat", "Lakshmi Venkat" }, new String[] { "yash.thakur", "Yash Thakur" });

    /** number, type, floor, price, capacity, description, amenities */
    private static final List<Object[]> ROOMS = List.of(
            new Object[] { "101", RoomType.STANDARD, 1, 3200, 2, "A bright, comfortable room with a queen bed and a work desk, close to the lobby.", "Wi-Fi, Air conditioning, TV, Tea and coffee" },
            new Object[] { "102", RoomType.STANDARD, 1, 3200, 2, "Queen bed, garden-facing window and a quiet corner desk.", "Wi-Fi, Air conditioning, TV, Garden view" },
            new Object[] { "103", RoomType.STANDARD, 1, 3500, 2, "Twin beds with a small seating area. Ideal for colleagues travelling together.", "Wi-Fi, Air conditioning, TV, Twin beds" },
            new Object[] { "104", RoomType.STANDARD, 1, 3500, 3, "Queen bed plus a sofa bed, for a small family.", "Wi-Fi, Air conditioning, TV, Sofa bed" },
            new Object[] { "105", RoomType.STANDARD, 1, 3800, 2, "Corner room with extra light and a larger bathroom.", "Wi-Fi, Air conditioning, TV, Bathtub" },
            new Object[] { "201", RoomType.DELUXE, 2, 5500, 3, "Spacious room with a king bed, a lounge chair and a city view.", "Wi-Fi, Air conditioning, TV, Minibar, City view" },
            new Object[] { "202", RoomType.DELUXE, 2, 5500, 3, "King bed, work desk and a balcony facing the pool.", "Wi-Fi, Air conditioning, TV, Minibar, Balcony" },
            new Object[] { "203", RoomType.DELUXE, 2, 5900, 3, "Large deluxe room with a reading nook and rainfall shower.", "Wi-Fi, Air conditioning, TV, Minibar, Rainfall shower" },
            new Object[] { "204", RoomType.DELUXE, 2, 5900, 2, "Quiet end-of-corridor room with a king bed and pool view.", "Wi-Fi, Air conditioning, TV, Minibar, Pool view" },
            new Object[] { "205", RoomType.DELUXE, 2, 6500, 3, "Top deluxe room with a private sit-out and premium linen.", "Wi-Fi, Air conditioning, TV, Minibar, Sit-out" },
            new Object[] { "206", RoomType.DELUXE, 2, 6500, 3, "Currently closed for renovation. Reopening soon.", "Wi-Fi, Air conditioning, TV" },
            new Object[] { "301", RoomType.SUITE, 3, 11000, 4, "A suite with a separate living room, king bed and a view of the skyline.", "Wi-Fi, Air conditioning, Smart TV, Minibar, Living room, Skyline view" },
            new Object[] { "302", RoomType.SUITE, 3, 12500, 4, "Corner suite with a dining table, kitchenette and wraparound windows.", "Wi-Fi, Air conditioning, Smart TV, Kitchenette, Dining area" },
            new Object[] { "303", RoomType.SUITE, 3, 14500, 4, "Our best suite: a master bedroom, a lounge, a bathtub with a view and butler service.", "Wi-Fi, Air conditioning, Smart TV, Bathtub, Butler service, Lounge" },
            new Object[] { "401", RoomType.FAMILY, 4, 8500, 5, "Two bedrooms joined by a living area, perfect for a family of five.", "Wi-Fi, Air conditioning, TV, Two bedrooms, Kids' corner" },
            new Object[] { "402", RoomType.FAMILY, 4, 8900, 6, "Two bedrooms with a big balcony and space for an extra bed.", "Wi-Fi, Air conditioning, TV, Two bedrooms, Balcony, Extra bed" },
            new Object[] { "403", RoomType.FAMILY, 4, 8500, 5, "Bright family room with bunk beds for children and a king bed for parents.", "Wi-Fi, Air conditioning, TV, Bunk beds" },
            new Object[] { "404", RoomType.SUITE, 4, 13000, 4, "Penthouse-level suite with a private terrace.", "Wi-Fi, Air conditioning, Smart TV, Private terrace, Minibar" });

    private static final List<String> CANCEL_REASONS = List.of("Change of travel plans", "Found a closer hotel", "Work trip was postponed", "Family emergency");
    private static final List<String> REQUESTS = List.of("Late check-in around 11 pm", "Ground floor if possible", "Extra pillows please", "Quiet room away from the lift", "Celebrating an anniversary");

    private final UserRepository users;
    private final RoomRepository rooms;
    private final BookingRepository bookings;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, RoomRepository rooms, BookingRepository bookings, PasswordEncoder encoder) {
        this.users = users;
        this.rooms = rooms;
        this.bookings = bookings;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.HOTEL_ADMIN, "admin123");
        seedUsers(GUESTS, Role.CUSTOMER, "customer123");
        if (rooms.count() == 0) {
            seedRoomsAndBookings();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    /** Dates already taken by confirmed stays, per room id. */
    private static boolean free(Map<Long, List<LocalDate[]>> taken, Room room, LocalDate in, LocalDate out) {
        return taken.getOrDefault(room.getId(), List.of()).stream().noneMatch(s -> s[0].isBefore(out) && in.isBefore(s[1]));
    }

    private Booking stay(Room room, User guest, LocalDate in, int nights, int guests, BookingStatus status, LocalDateTime made, String by, String reason, String request) {
        Booking b = new Booking(room, guest, in, in.plusDays(nights), guests, room.getPricePerNight().multiply(BigDecimal.valueOf(nights)), nights, request);
        b.forceState(status, made, by, reason);
        return b;
    }

    private void seedRoomsAndBookings() {
        Random random = new Random(8);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Room> saved = new ArrayList<>();
        for (Object[] r : ROOMS) {
            Room room = new Room((String) r[0], (RoomType) r[1], (Integer) r[2], BigDecimal.valueOf((Integer) r[3]), (Integer) r[4], (String) r[5], (String) r[6]);
            if (r[0].equals("206")) {
                room.update("206", RoomType.DELUXE, 2, BigDecimal.valueOf(6500), 3, (String) r[5], (String) r[6], false);
            }
            saved.add(room);
        }
        saved = rooms.saveAll(saved);
        List<User> guests = GUESTS.stream().map(g -> users.findByUsername(g[0]).orElseThrow()).toList();
        User aarav = guests.get(0);
        Map<Long, List<LocalDate[]>> taken = new HashMap<>();
        List<Booking> toSave = new ArrayList<>();
        Room r302 = saved.stream().filter(r -> r.getNumber().equals("302")).findFirst().orElseThrow();
        Room r103 = saved.stream().filter(r -> r.getNumber().equals("103")).findFirst().orElseThrow();
        Room r204 = saved.stream().filter(r -> r.getNumber().equals("204")).findFirst().orElseThrow();
        Room r401 = saved.stream().filter(r -> r.getNumber().equals("401")).findFirst().orElseThrow();

        // aarav: an upcoming confirmed stay, a past one, a pending request and a cancelled booking
        toSave.add(stay(r302, aarav, today.plusDays(10), 3, 3, BookingStatus.CONFIRMED, now.minusDays(4), "hoteladmin", null, "Late check-in around 11 pm"));
        taken.computeIfAbsent(r302.getId(), k -> new ArrayList<>()).add(new LocalDate[] { today.plusDays(10), today.plusDays(13) });
        toSave.add(stay(r103, aarav, today.minusDays(30), 3, 2, BookingStatus.CONFIRMED, now.minusDays(40), "hoteladmin", null, null));
        taken.computeIfAbsent(r103.getId(), k -> new ArrayList<>()).add(new LocalDate[] { today.minusDays(30), today.minusDays(27) });
        toSave.add(stay(r204, aarav, today.plusDays(22), 2, 2, BookingStatus.PENDING, now.minusHours(20), null, null, "Quiet room away from the lift"));
        toSave.add(stay(r401, aarav, today.plusDays(35), 4, 4, BookingStatus.CANCELLED, now.minusDays(9), "aarav.sharma", "Work trip was postponed", null));

        // every other room: a run of stays from three months ago to two months ahead
        for (Room room : saved) {
            if (!room.isActive()) {
                continue;
            }
            LocalDate cursor = today.minusDays(85 + random.nextInt(10));
            while (cursor.isBefore(today.plusDays(60))) {
                cursor = cursor.plusDays(random.nextInt(4));
                int nights = 1 + random.nextInt(5);
                LocalDate out = cursor.plusDays(nights);
                User guest = guests.get(1 + random.nextInt(guests.size() - 1));
                int party = 1 + random.nextInt(room.getCapacity());
                int roll = random.nextInt(100);
                if (roll < 10) { // a cancelled booking never blocks the room
                    toSave.add(stay(room, guest, cursor, nights, party, BookingStatus.CANCELLED, cursor.minusDays(8).atTime(10, 0),
                            guest.getUsername(), CANCEL_REASONS.get(random.nextInt(CANCEL_REASONS.size())), null));
                } else if (free(taken, room, cursor, out)) {
                    toSave.add(stay(room, guest, cursor, nights, party, BookingStatus.CONFIRMED, cursor.minusDays(3 + random.nextInt(14)).atTime(9, 30),
                            ADMINS.get(random.nextInt(ADMINS.size()))[0], null, random.nextInt(4) == 0 ? REQUESTS.get(random.nextInt(REQUESTS.size())) : null));
                    taken.computeIfAbsent(room.getId(), k -> new ArrayList<>()).add(new LocalDate[] { cursor, out });
                }
                cursor = out.plusDays(random.nextInt(3));
            }
        }

        // pending requests in free windows; the last two ask for the same room and dates
        List<Room> active = saved.stream().filter(Room::isActive).toList();
        int made = 0;
        Room contested = null;
        LocalDate contestedIn = null;
        for (int attempt = 0; attempt < 200 && made < 8; attempt++) {
            Room room = active.get(random.nextInt(active.size()));
            LocalDate in = today.plusDays(3 + random.nextInt(45));
            int nights = 2 + random.nextInt(3);
            if (!free(taken, room, in, in.plusDays(nights))) {
                continue;
            }
            User guest = guests.get(1 + random.nextInt(guests.size() - 1));
            toSave.add(stay(room, guest, in, nights, 1 + random.nextInt(room.getCapacity()), BookingStatus.PENDING, now.minusHours(2 + random.nextInt(60)), null, null, null));
            made++;
            if (made == 7) {
                contested = room;
                contestedIn = in;
            }
            if (made == 8 && contested != null) {
                toSave.remove(toSave.size() - 1); // the eighth is the rival request for the seventh's room and dates
                User rival = guests.get(1 + random.nextInt(guests.size() - 1));
                toSave.add(stay(contested, rival, contestedIn, 2, 1, BookingStatus.PENDING, now.minusHours(1), null, null, "Hoping for this room, it is our anniversary"));
            }
        }
        bookings.saveAll(toSave);
    }
}
