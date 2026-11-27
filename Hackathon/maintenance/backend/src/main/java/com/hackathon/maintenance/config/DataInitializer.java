package com.hackathon.maintenance.config;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.maintenance.entity.Category;
import com.hackathon.maintenance.entity.MaintenanceRequest;
import com.hackathon.maintenance.entity.Priority;
import com.hackathon.maintenance.entity.RequestStatus;
import com.hackathon.maintenance.entity.Role;
import com.hackathon.maintenance.entity.Technician;
import com.hackathon.maintenance.entity.User;
import com.hackathon.maintenance.repo.MaintenanceRequestRepository;
import com.hackathon.maintenance.repo.TechnicianRepository;
import com.hackathon.maintenance.repo.UserRepository;

/**
 * Seeds two facility managers, 30 residents, nine technicians (one unavailable) and 40 requests in every state: open ones of
 * different ages (fresh, ageing, stale), assigned, in progress, resolved and closed, with resolution notes. aarav.sharma has one
 * request in each of the five states. Idempotent: users and technicians only when missing, requests only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> MANAGERS = List.of(
            new String[] { "facility", "Prakash Menon" },
            new String[] { "ravi.menon", "Ravi Menon" });

    private static final List<String[]> RESIDENTS = List.of(
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

    /** name, trade, phone, active */
    private static final List<Object[]> TECHNICIANS = List.of(
            new Object[] { "Ramesh Pillai", Category.PLUMBING, "+91 98450 11201", true },
            new Object[] { "Dinesh Kumar", Category.PLUMBING, "+91 98450 11202", false },
            new Object[] { "Suresh Naik", Category.ELECTRICAL, "+91 98450 11203", true },
            new Object[] { "Otis Service Desk", Category.LIFT, "+91 80 4000 5500", true },
            new Object[] { "Geeta Cleaning Crew", Category.CLEANING, "+91 98450 11205", true },
            new Object[] { "Mohan Lal", Category.GARDEN, "+91 98450 11206", true },
            new Object[] { "Securitas Control Room", Category.SECURITY, "+91 80 4000 7700", true },
            new Object[] { "Anil Appliance Repairs", Category.APPLIANCE, "+91 98450 11208", true },
            new Object[] { "Vikas Yadav", Category.OTHER, "+91 98450 11209", true });

    /** category, description, priority, resolution notes */
    private static final List<Object[]> ISSUES = List.of(
            new Object[] { Category.PLUMBING, "Water is leaking from the bathroom ceiling. It looks like it is coming from the flat above.", Priority.HIGH, "Replaced a corroded joint in the flat above and repaired the ceiling plaster." },
            new Object[] { Category.ELECTRICAL, "The living room lights flicker whenever the AC starts.", Priority.MEDIUM, "Tightened a loose neutral at the distribution board and replaced the MCB." },
            new Object[] { Category.LIFT, "Lift 2 stops between floors and the doors take a long time to open.", Priority.EMERGENCY, "Door sensor replaced and the controller recalibrated by the lift vendor. Tested for a full day." },
            new Object[] { Category.CLEANING, "Garbage chute on the 5th floor is overflowing and smells.", Priority.MEDIUM, "Chute cleared and disinfected. Pickup frequency increased to twice a day." },
            new Object[] { Category.GARDEN, "The garden sprinklers near the children's play area do not switch off.", Priority.LOW, "Replaced the faulty timer and re-set the schedule." },
            new Object[] { Category.SECURITY, "The gate camera facing the visitor parking has been black since yesterday.", Priority.HIGH, "Replaced the camera power adapter and restored the recording." },
            new Object[] { Category.APPLIANCE, "The community-room geyser trips the breaker every time it heats.", Priority.MEDIUM, "Replaced the heating element and the thermostat." },
            new Object[] { Category.PLUMBING, "No hot water in the kitchen tap for a week.", Priority.MEDIUM, "Flushed the line and replaced the mixer cartridge." },
            new Object[] { Category.ELECTRICAL, "The stairwell light between the 3rd and 4th floor has been out for days.", Priority.LOW, "Replaced the fixture and the sensor." },
            new Object[] { Category.OTHER, "The main door lock of the tower sticks and is hard to open from outside.", Priority.MEDIUM, "Lubricated and replaced the latch." },
            new Object[] { Category.CLEANING, "Moss on the walkway near the clubhouse makes it slippery.", Priority.HIGH, "Pressure-washed the walkway and applied an anti-slip treatment." },
            new Object[] { Category.PLUMBING, "The basement drain is blocked and the water is not draining away.", Priority.EMERGENCY, "Cleared the blockage with a drain machine and cleaned the sump." });

    private static final List<String> BLOCKS = List.of("Block A", "Block B", "Block C");

    private final UserRepository users;
    private final TechnicianRepository technicians;
    private final MaintenanceRequestRepository requests;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, TechnicianRepository technicians, MaintenanceRequestRepository requests, PasswordEncoder encoder) {
        this.users = users;
        this.technicians = technicians;
        this.requests = requests;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(MANAGERS, Role.FACILITY_MANAGER, "manager123");
        seedUsers(RESIDENTS, Role.RESIDENT, "resident123");
        if (technicians.count() == 0) {
            for (Object[] t : TECHNICIANS) {
                Technician technician = new Technician((String) t[0], (Category) t[1], (String) t[2]);
                technician.setActive((Boolean) t[3]);
                technicians.save(technician);
            }
        }
        if (requests.count() == 0) {
            seedRequests();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private Technician technicianFor(Category category, List<Technician> active) {
        return active.stream().filter(t -> t.getTrade() == category).findFirst()
                .orElseGet(() -> active.stream().filter(t -> t.getTrade() == Category.OTHER).findFirst().orElseThrow());
    }

    private void seedRequests() {
        Random random = new Random(10);
        LocalDateTime now = LocalDateTime.now();
        List<User> residents = RESIDENTS.stream().map(r -> users.findByUsername(r[0]).orElseThrow()).toList();
        List<Technician> active = technicians.findAllByOrderByNameAsc().stream().filter(Technician::isActive).toList();

        // 7 open, 6 assigned, 7 in progress, 6 resolved, 14 closed; aarav gets indexes 0, 7, 13, 20, 26
        List<RequestStatus> plan = new ArrayList<>();
        for (int i = 0; i < 7; i++) plan.add(RequestStatus.OPEN);
        for (int i = 0; i < 6; i++) plan.add(RequestStatus.ASSIGNED);
        for (int i = 0; i < 7; i++) plan.add(RequestStatus.IN_PROGRESS);
        for (int i = 0; i < 6; i++) plan.add(RequestStatus.RESOLVED);
        for (int i = 0; i < 14; i++) plan.add(RequestStatus.CLOSED);
        List<Integer> aaravAt = List.of(0, 7, 13, 20, 26);
        int[] openAgesHours = { 4, 22, 40, 75, 130, 200, 10 };

        List<MaintenanceRequest> toSave = new ArrayList<>();
        for (int i = 0; i < plan.size(); i++) {
            Object[] issue = ISSUES.get(i % ISSUES.size());
            RequestStatus status = plan.get(i);
            int who = aaravAt.contains(i) ? 0 : 1 + random.nextInt(residents.size() - 1);
            User resident = residents.get(who);
            String location = BLOCKS.get(who % BLOCKS.size()) + ", Flat " + (1 + who % 8) + "0" + (1 + who % 4);
            Category category = (Category) issue[0];
            MaintenanceRequest r = new MaintenanceRequest(category, (String) issue[1], location, (Priority) issue[2], resident);

            LocalDateTime created = switch (status) {
                case OPEN -> now.minusHours(openAgesHours[i]);
                case ASSIGNED -> now.minusHours(20 + random.nextInt(60));
                case IN_PROGRESS -> now.minusHours(30 + random.nextInt(90));
                case RESOLVED -> now.minusDays(2 + random.nextInt(6));
                case CLOSED -> now.minusDays(10 + random.nextInt(70));
            };
            Technician tech = status == RequestStatus.OPEN ? null : technicianFor(category, active);
            LocalDateTime assigned = tech == null ? null : created.plusHours(2 + random.nextInt(6));
            LocalDateTime started = status == RequestStatus.OPEN || status == RequestStatus.ASSIGNED ? null : assigned.plusHours(3 + random.nextInt(20));
            LocalDateTime resolved = status == RequestStatus.RESOLVED || status == RequestStatus.CLOSED ? started.plusHours(4 + random.nextInt(36)) : null;
            LocalDateTime closed = status == RequestStatus.CLOSED ? resolved.plusDays(1 + random.nextInt(3)) : null;
            r.forceState(status, tech, created, assigned, started, resolved, closed, resolved == null ? null : (String) issue[3]);
            toSave.add(r);
        }
        requests.saveAll(toSave);
    }
}
