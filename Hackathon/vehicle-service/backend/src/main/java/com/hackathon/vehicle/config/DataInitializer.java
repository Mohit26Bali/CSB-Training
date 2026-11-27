package com.hackathon.vehicle.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import com.hackathon.vehicle.entity.BookingStatus;
import com.hackathon.vehicle.entity.Center;
import com.hackathon.vehicle.entity.Role;
import com.hackathon.vehicle.entity.ServiceBooking;
import com.hackathon.vehicle.entity.ServiceType;
import com.hackathon.vehicle.entity.User;
import com.hackathon.vehicle.entity.Vehicle;
import com.hackathon.vehicle.entity.VehicleType;
import com.hackathon.vehicle.repo.CenterRepository;
import com.hackathon.vehicle.repo.ServiceBookingRepository;
import com.hackathon.vehicle.repo.ServiceTypeRepository;
import com.hackathon.vehicle.repo.UserRepository;
import com.hackathon.vehicle.repo.VehicleRepository;

/**
 * Seeds two service admins, 30 customers with 40 vehicles, 8 services, a 5-bay center and about 90 bookings: three months of finished
 * services, two vehicles in the workshop today, arrivals today and bookings over the next weeks, plus a few cancellations. No vehicle
 * is booked twice for the same day and no day uses more than 4 of the 5 bays. aarav.sharma has a Swift and a Royal Enfield with an
 * upcoming booking, two finished services (history) and a cancelled one. Idempotent: users only when missing, the rest only when empty.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "serviceadmin", "Ramesh Naik" },
            new String[] { "garage", "Sunita Deshpande" });

    private static final List<String[]> CUSTOMERS = List.of(
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

    /** name, description, days, base price (for a car) */
    private static final List<Object[]> SERVICES = List.of(
            new Object[] { "Basic service", "Oil change, filter check, fluid top-up and a 25-point inspection.", 1, 2500 },
            new Object[] { "Full service", "Everything in the basic service plus brakes, suspension, spark plugs and a detailed wash.", 2, 6500 },
            new Object[] { "Oil and filter change", "Engine oil and oil filter replaced with the grade your maker recommends.", 1, 1800 },
            new Object[] { "Brake service", "Pads, discs and brake fluid checked and replaced where needed.", 1, 3200 },
            new Object[] { "Wheel alignment and balancing", "Computerised alignment and balancing of all four wheels.", 1, 1500 },
            new Object[] { "AC service", "Gas top-up, cabin filter and a cooling-coil clean.", 1, 2800 },
            new Object[] { "Battery replacement", "New battery fitted, charging system tested.", 1, 4500 },
            new Object[] { "Engine overhaul", "Full engine strip-down, part replacement and a road test.", 4, 28000 });

    /** type, make, model */
    private static final List<Object[]> MODELS = List.of(
            new Object[] { VehicleType.CAR, "Maruti Suzuki", "Swift" }, new Object[] { VehicleType.CAR, "Hyundai", "i20" },
            new Object[] { VehicleType.CAR, "Honda", "City" }, new Object[] { VehicleType.CAR, "Maruti Suzuki", "Baleno" },
            new Object[] { VehicleType.CAR, "Tata", "Altroz" }, new Object[] { VehicleType.SUV, "Tata", "Nexon" },
            new Object[] { VehicleType.SUV, "Mahindra", "XUV700" }, new Object[] { VehicleType.SUV, "Hyundai", "Creta" },
            new Object[] { VehicleType.VAN, "Toyota", "Innova Crysta" }, new Object[] { VehicleType.VAN, "Maruti Suzuki", "Eeco" },
            new Object[] { VehicleType.BIKE, "Bajaj", "Pulsar 150" }, new Object[] { VehicleType.BIKE, "TVS", "Apache RTR 160" },
            new Object[] { VehicleType.BIKE, "Hero", "Splendor Plus" }, new Object[] { VehicleType.BIKE, "Honda", "Activa 6G" },
            new Object[] { VehicleType.BIKE, "Royal Enfield", "Classic 350" });

    private static final List<String> WORK_NOTES = List.of("Everything checked, no further issues.", "Replaced air filter as well.",
            "Front brake pads were thin and have been replaced.", "Tyre pressure corrected, wipers replaced.",
            "Minor oil seepage noticed, to be watched at the next visit.", "Battery terminals cleaned.");
    private static final List<String> CUSTOMER_NOTES = List.of("Strange noise from the front left wheel", "Please check the AC as well",
            "Pick-up before 6 pm", "Brake feels soft", "Routine service", "Warning light on the dashboard");
    private static final List<String> CANCEL_REASONS = List.of("Out of town that week", "Sold the vehicle", "Got it done elsewhere", "Rescheduling");

    private final UserRepository users;
    private final VehicleRepository vehicles;
    private final ServiceTypeRepository services;
    private final ServiceBookingRepository bookings;
    private final CenterRepository centers;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, VehicleRepository vehicles, ServiceTypeRepository services,
                           ServiceBookingRepository bookings, CenterRepository centers, PasswordEncoder encoder) {
        this.users = users;
        this.vehicles = vehicles;
        this.services = services;
        this.bookings = bookings;
        this.centers = centers;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.SERVICE_ADMIN, "admin123");
        seedUsers(CUSTOMERS, Role.CUSTOMER, "customer123");
        if (centers.count() == 0) {
            centers.save(new Center(5));
        }
        if (services.count() == 0) {
            services.saveAll(SERVICES.stream().map(s -> new ServiceType((String) s[0], (String) s[1], (Integer) s[2], BigDecimal.valueOf((Integer) s[3]), true)).toList());
        }
        if (vehicles.count() == 0) {
            seedVehiclesAndBookings();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    /** Remembers what is already booked so the demo data never breaks the rules the service enforces. */
    private final class Plan {
        final Map<LocalDate, Integer> perDay = new HashMap<>();
        final Map<Long, List<LocalDate[]>> perVehicle = new HashMap<>();
        final List<ServiceBooking> made = new ArrayList<>();

        boolean place(Vehicle vehicle, ServiceType type, LocalDate start, BookingStatus status, String by, Random random) {
            LocalDate end = start.plusDays(type.getDurationDays() - 1L);
            boolean live = status != BookingStatus.CANCELLED;
            if (live) {
                boolean clash = perVehicle.getOrDefault(vehicle.getId(), List.of()).stream().anyMatch(r -> !r[0].isAfter(end) && !r[1].isBefore(start));
                boolean full = start.datesUntil(end.plusDays(1)).anyMatch(d -> perDay.getOrDefault(d, 0) >= 4);
                if (clash || full) {
                    return false;
                }
                perVehicle.computeIfAbsent(vehicle.getId(), k -> new ArrayList<>()).add(new LocalDate[] { start, end });
                start.datesUntil(end.plusDays(1)).forEach(d -> perDay.merge(d, 1, Integer::sum));
            }
            BigDecimal estimate = type.estimateFor(vehicle.getType());
            String note = random.nextInt(3) == 0 ? CUSTOMER_NOTES.get(random.nextInt(CUSTOMER_NOTES.size())) : null;
            ServiceBooking b = new ServiceBooking(vehicle, vehicle.getOwner(), type, start, estimate, note);
            LocalDateTime made = start.minusDays(2 + random.nextInt(9)).atTime(10 + random.nextInt(8), random.nextInt(60));
            BigDecimal finalCost = estimate.multiply(BigDecimal.valueOf(95 + random.nextInt(21)).movePointLeft(2)).setScale(2, RoundingMode.HALF_UP);
            b.forceState(status, made, by, finalCost, WORK_NOTES.get(random.nextInt(WORK_NOTES.size())), CANCEL_REASONS.get(random.nextInt(CANCEL_REASONS.size())));
            this.made.add(b);
            return true;
        }
    }

    private void seedVehiclesAndBookings() {
        Random random = new Random(2);
        LocalDate today = LocalDate.now();
        List<ServiceType> types = services.findAllByOrderByNameAsc();
        ServiceType basic = type(types, "Basic service");
        ServiceType full = type(types, "Full service");
        ServiceType oil = type(types, "Oil and filter change");
        ServiceType brake = type(types, "Brake service");
        List<User> customers = users.findAll().stream().filter(u -> u.getRole() == Role.CUSTOMER).sorted((a, b) -> a.getUsername().compareTo(b.getUsername())).toList();
        User aarav = customers.stream().filter(u -> u.getUsername().equals("aarav.sharma")).findFirst().orElseThrow();

        List<Vehicle> fleet = new ArrayList<>();
        Vehicle swift = new Vehicle(aarav, "MH12AB1234", VehicleType.CAR, "Maruti Suzuki", "Swift", 2019);
        Vehicle enfield = new Vehicle(aarav, "MH14XY4321", VehicleType.BIKE, "Royal Enfield", "Classic 350", 2021);
        fleet.add(swift);
        fleet.add(enfield);
        int serial = 1000;
        for (int i = 0; i < customers.size(); i++) {
            User owner = customers.get(i);
            if (owner == aarav) {
                continue;
            }
            int count = i % 3 == 0 ? 2 : 1;
            for (int k = 0; k < count; k++) {
                Object[] m = MODELS.get(random.nextInt(MODELS.size()));
                String number = (k == 0 ? "MH12" : "MH14") + (char) ('A' + random.nextInt(26)) + (char) ('A' + random.nextInt(26)) + (serial++);
                fleet.add(new Vehicle(owner, number, (VehicleType) m[0], (String) m[1], (String) m[2], 2014 + random.nextInt(11)));
            }
        }
        vehicles.saveAll(fleet);

        Plan plan = new Plan();
        String admin = "serviceadmin";
        // aarav.sharma: an upcoming booking, two finished services and a cancelled one
        plan.place(swift, full, today.plusDays(6), BookingStatus.BOOKED, admin, random);
        plan.place(swift, basic, today.minusDays(45), BookingStatus.COMPLETED, admin, random);
        plan.place(swift, oil, today.minusDays(130), BookingStatus.COMPLETED, admin, random);
        plan.place(enfield, basic, today.minusDays(20), BookingStatus.CANCELLED, "aarav.sharma", random);
        // in the workshop today and arriving today
        List<Vehicle> others = fleet.stream().filter(v -> v.getOwner() != aarav).toList();
        plan.place(others.get(0), full, today.minusDays(1), BookingStatus.IN_SERVICE, "garage", random);
        plan.place(others.get(1), brake, today, BookingStatus.IN_SERVICE, "garage", random);
        plan.place(others.get(2), basic, today, BookingStatus.BOOKED, admin, random);
        plan.place(others.get(3), oil, today, BookingStatus.BOOKED, admin, random);

        for (int i = 4; i < others.size(); i++) {
            Vehicle v = others.get(i);
            int past = random.nextInt(4);
            for (int k = 0; k < past; k++) {
                plan.place(v, types.get(random.nextInt(types.size() - 1)), today.minusDays(8 + random.nextInt(105)), BookingStatus.COMPLETED,
                        random.nextBoolean() ? admin : "garage", random);
            }
            if (random.nextInt(4) == 0) {
                plan.place(v, types.get(random.nextInt(types.size() - 1)), today.minusDays(5 + random.nextInt(60)), BookingStatus.CANCELLED,
                        v.getOwner().getUsername(), random);
            }
            if (random.nextInt(3) != 0) {
                plan.place(v, types.get(random.nextInt(types.size())), today.plusDays(1 + random.nextInt(40)), BookingStatus.BOOKED, admin, random);
            }
        }
        bookings.saveAll(plan.made);
    }

    private static ServiceType type(List<ServiceType> types, String name) {
        return types.stream().filter(t -> t.getName().equals(name)).findFirst().orElseThrow();
    }
}
