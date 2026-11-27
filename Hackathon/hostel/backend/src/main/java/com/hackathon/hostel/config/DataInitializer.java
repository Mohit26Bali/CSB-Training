package com.hackathon.hostel.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.hostel.entity.Role;
import com.hackathon.hostel.entity.Room;
import com.hackathon.hostel.entity.RoomAllocation;
import com.hackathon.hostel.entity.RoomRequest;
import com.hackathon.hostel.entity.RoomType;
import com.hackathon.hostel.entity.User;
import com.hackathon.hostel.repo.RoomAllocationRepository;
import com.hackathon.hostel.repo.RoomRepository;
import com.hackathon.hostel.repo.RoomRequestRepository;
import com.hackathon.hostel.repo.UserRepository;

/**
 * Seeds wardens, 30 students, 24 rooms on four floors (one under maintenance), most students already living in a room,
 * and a few pending, rejected and cancelled requests. aarav.sharma has no room and no request, so the full flow can be tried.
 * Idempotent: users only when missing, rooms and requests only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "warden", "Sunita Rao" },
            new String[] { "mahesh.kulkarni", "Mahesh Kulkarni" });

    private static final List<String[]> STUDENTS = List.of(
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

    private static final Set<String> PENDING = Set.of("rohan.mehta", "isha.agarwal", "pranav.joshi", "yash.thakur");
    private static final Set<String> REJECTED = Set.of("lakshmi.venkat");
    private static final Set<String> CANCELLED = Set.of("rajesh.nambiar");

    private static final List<String> NOTES = List.of(
            "I prefer a lower floor because of a knee injury.",
            "Final-year student, need a quiet room to study.",
            "Joining the new batch from next week.",
            "Would like to be close to the library block.",
            "My previous room had a plumbing problem.");

    private final UserRepository users;
    private final RoomRepository rooms;
    private final RoomRequestRepository requests;
    private final RoomAllocationRepository allocations;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, RoomRepository rooms, RoomRequestRepository requests,
                           RoomAllocationRepository allocations, PasswordEncoder encoder) {
        this.users = users;
        this.rooms = rooms;
        this.requests = requests;
        this.allocations = allocations;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.HOSTEL_ADMIN, "admin123");
        seedUsers(STUDENTS, Role.STUDENT, "student123");
        if (rooms.count() == 0) {
            seedRoomsAndPeople();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedRoomsAndPeople() {
        Random random = new Random(3);
        LocalDateTime now = LocalDateTime.now();

        List<Room> created = new ArrayList<>();
        for (int floor = 1; floor <= 4; floor++) {
            created.add(new Room("A-" + floor + "01", floor, RoomType.SINGLE, 1));
            created.add(new Room("A-" + floor + "02", floor, RoomType.SINGLE, 1));
            created.add(new Room("A-" + floor + "03", floor, RoomType.DOUBLE, 2));
            created.add(new Room("A-" + floor + "04", floor, RoomType.DOUBLE, 2));
            created.add(new Room("A-" + floor + "05", floor, RoomType.TRIPLE, 3));
            created.add(new Room("A-" + floor + "06", floor, RoomType.DORMITORY, 6));
        }
        Room maintenance = created.get(23); // A-406, the top-floor dormitory
        maintenance.update(maintenance.getNumber(), maintenance.getFloor(), maintenance.getType(), maintenance.getCapacity(), false);
        List<Room> saved = rooms.saveAll(created);

        List<Room> usable = saved.stream().filter(Room::isActive).toList();
        int[] taken = new int[usable.size()];
        List<User> students = STUDENTS.stream().map(s -> users.findByUsername(s[0]).orElseThrow()).toList();
        List<RoomAllocation> toAllocate = new ArrayList<>();
        List<RoomRequest> toRequest = new ArrayList<>();

        int pendingDay = 6;
        for (User student : students) {
            String name = student.getUsername();
            if (name.equals("aarav.sharma")) {
                continue; // free: can ask for a room
            }
            if (PENDING.contains(name)) {
                RoomRequest r = new RoomRequest(student, random.nextBoolean() ? RoomType.values()[random.nextInt(3)] : null,
                        NOTES.get(random.nextInt(NOTES.size())));
                r.setCreatedAt(now.minusDays(pendingDay--).withHour(10 + random.nextInt(6)));
                toRequest.add(r);
            } else if (REJECTED.contains(name)) {
                RoomRequest r = new RoomRequest(student, RoomType.SINGLE, "Prefer a single room.");
                r.setCreatedAt(now.minusDays(12));
                r.reject("warden", "No single rooms are free this semester. Please apply again next month.");
                r.setDecidedAt(now.minusDays(10));
                toRequest.add(r);
            } else if (CANCELLED.contains(name)) {
                RoomRequest r = new RoomRequest(student, null, "Changed my mind, staying with family.");
                r.setCreatedAt(now.minusDays(9));
                r.cancel();
                toRequest.add(r);
            } else {
                int index;
                do {
                    index = random.nextInt(usable.size());
                } while (taken[index] >= usable.get(index).getCapacity());
                taken[index]++;
                RoomRequest r = new RoomRequest(student, usable.get(index).getType(), null);
                r.setCreatedAt(now.minusDays(40 + random.nextInt(30)));
                r.approve(ADMINS.get(random.nextInt(ADMINS.size()))[0], usable.get(index));
                r.setDecidedAt(r.getCreatedAt().plusDays(1));
                toRequest.add(r);
                RoomAllocation a = new RoomAllocation(usable.get(index), student, r.getDecidedBy());
                a.setAllocatedAt(r.getDecidedAt());
                toAllocate.add(a);
            }
        }
        requests.saveAll(toRequest);
        allocations.saveAll(toAllocate);
    }
}
