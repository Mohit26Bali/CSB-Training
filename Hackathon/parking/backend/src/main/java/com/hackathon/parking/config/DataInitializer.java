package com.hackathon.parking.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.parking.entity.ParkingAllocation;
import com.hackathon.parking.entity.ParkingRequest;
import com.hackathon.parking.entity.ParkingSlot;
import com.hackathon.parking.entity.Role;
import com.hackathon.parking.entity.SlotStatus;
import com.hackathon.parking.entity.User;
import com.hackathon.parking.repo.ParkingAllocationRepository;
import com.hackathon.parking.repo.ParkingRequestRepository;
import com.hackathon.parking.repo.SlotRepository;
import com.hackathon.parking.repo.UserRepository;

/**
 * Seeds admins, 30 employees, 32 slots on four floors (two reserved, two under maintenance), most employees already
 * parked with a vehicle number, and a few pending, rejected and cancelled requests. aarav.sharma has no slot and no
 * request, so the whole flow can be tried. Idempotent: users only when missing, slots and requests only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "parkadmin", "Vikram Sethi" },
            new String[] { "lata.deshmukh", "Lata Deshmukh" });

    private static final List<String[]> EMPLOYEES = List.of(
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
            "I drive in every day and the street parking is full.",
            "Two-wheeler, any covered spot is fine.",
            "Need a spot near the lift, I have a knee injury.",
            "Shifting to the night team from next week.",
            "Carpooling with a colleague, only need one slot.");

    private final UserRepository users;
    private final SlotRepository slots;
    private final ParkingRequestRepository requests;
    private final ParkingAllocationRepository allocations;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, SlotRepository slots, ParkingRequestRepository requests,
                           ParkingAllocationRepository allocations, PasswordEncoder encoder) {
        this.users = users;
        this.slots = slots;
        this.requests = requests;
        this.allocations = allocations;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.PARKING_ADMIN, "admin123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (slots.count() == 0) {
            seedSlotsAndPeople();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private String vehicle(Random random, Set<String> used) {
        String number;
        do {
            number = "MH" + (12 + random.nextInt(3)) + (char) ('A' + random.nextInt(26)) + (char) ('A' + random.nextInt(26))
                    + (1000 + random.nextInt(9000));
        } while (!used.add(number));
        return number;
    }

    private void seedSlotsAndPeople() {
        Random random = new Random(6);
        LocalDateTime now = LocalDateTime.now();
        Set<String> usedVehicles = new HashSet<>();

        List<ParkingSlot> created = new ArrayList<>();
        for (int floor = 0; floor <= 3; floor++) {
            for (int n = 1; n <= 8; n++) {
                SlotStatus status = SlotStatus.AVAILABLE;
                if (floor == 0 && n <= 2) {
                    status = SlotStatus.RESERVED; // visitor and CEO bays at the entrance
                } else if (floor == 3 && n >= 7) {
                    status = SlotStatus.MAINTENANCE; // roof leak
                }
                created.add(new ParkingSlot("F" + floor + "-" + String.format("%02d", n), floor, status));
            }
        }
        List<ParkingSlot> saved = slots.saveAll(created);
        List<ParkingSlot> usable = saved.stream().filter(s -> s.getStatus() == SlotStatus.AVAILABLE).toList();
        boolean[] taken = new boolean[usable.size()];

        List<ParkingRequest> toRequest = new ArrayList<>();
        List<ParkingAllocation> toAllocate = new ArrayList<>();
        int pendingDay = 6;
        for (String[] person : EMPLOYEES) {
            User employee = users.findByUsername(person[0]).orElseThrow();
            String name = employee.getUsername();
            if (name.equals("aarav.sharma")) {
                continue; // free: can ask for a slot
            }
            String vehicle = vehicle(random, usedVehicles);
            if (PENDING.contains(name)) {
                ParkingRequest r = new ParkingRequest(employee, vehicle, random.nextBoolean() ? random.nextInt(4) : null, NOTES.get(random.nextInt(NOTES.size())));
                r.setCreatedAt(now.minusDays(pendingDay--).withHour(9 + random.nextInt(8)));
                toRequest.add(r);
            } else if (REJECTED.contains(name)) {
                ParkingRequest r = new ParkingRequest(employee, vehicle, 0, "Please near the entrance.");
                r.setCreatedAt(now.minusDays(12));
                r.reject("parkadmin", "Entrance bays are reserved for visitors. Please ask for another floor.");
                r.setDecidedAt(now.minusDays(10));
                toRequest.add(r);
            } else if (CANCELLED.contains(name)) {
                ParkingRequest r = new ParkingRequest(employee, vehicle, null, "Started cycling to work.");
                r.setCreatedAt(now.minusDays(9));
                r.cancel();
                toRequest.add(r);
            } else {
                int index;
                do {
                    index = random.nextInt(usable.size());
                } while (taken[index]);
                taken[index] = true;
                ParkingRequest r = new ParkingRequest(employee, vehicle, usable.get(index).getFloor(), null);
                r.setCreatedAt(now.minusDays(30 + random.nextInt(60)));
                r.approve(ADMINS.get(random.nextInt(ADMINS.size()))[0], usable.get(index));
                r.setDecidedAt(r.getCreatedAt().plusDays(1));
                toRequest.add(r);
                ParkingAllocation a = new ParkingAllocation(usable.get(index), employee, vehicle, r.getDecidedBy());
                a.setAllocatedAt(r.getDecidedAt());
                toAllocate.add(a);
            }
        }
        requests.saveAll(toRequest);
        allocations.saveAll(toAllocate);
    }
}
