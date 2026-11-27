package com.hackathon.visitors.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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

import com.hackathon.visitors.entity.Role;
import com.hackathon.visitors.entity.User;
import com.hackathon.visitors.entity.Visit;
import com.hackathon.visitors.entity.VisitStatus;
import com.hackathon.visitors.repo.UserRepository;
import com.hackathon.visitors.repo.VisitRepository;

/**
 * Seeds two security officers, 30 employees and about 55 visits: a busy "today" (some already out, some inside, some still
 * expected, one cancelled), two weeks of history with a few no-shows, and invitations for the coming days. aarav.sharma has
 * four: one expected today with the fixed pass VP-100001, one inside now, one from yesterday and one for tomorrow.
 * Idempotent: users only when missing, visits only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> GUARDS = List.of(
            new String[] { "security", "Naveen Kumar" },
            new String[] { "rohit.gate", "Rohit Chauhan" });

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

    /** visitor, company */
    private static final List<String[]> VISITORS = List.of(
            new String[] { "Rakesh Bansal", "Bansal Consultants" }, new String[] { "Sunita Verma", "Verma Interiors" },
            new String[] { "Imran Sheikh", "Sheikh Logistics" }, new String[] { "Priyanka Das", "Das & Co. Chartered Accountants" },
            new String[] { "Alok Tripathi", "Tripathi Software" }, new String[] { "Fiona Dsouza", "Dsouza Recruiting" },
            new String[] { "Harish Rao", "Rao Electricals" }, new String[] { "Meghna Kapadia", "Kapadia Legal" },
            new String[] { "Vinod Pillai", "Pillai Packaging" }, new String[] { "Zainab Qureshi", "Qureshi Catering" },
            new String[] { "Tarun Mehra", "Mehra Printing Press" }, new String[] { "Deepa Krishnan", "Krishnan Training" },
            new String[] { "Omkar Joshi", "Joshi Stationers" }, new String[] { "Shalini Bose", "Bose Design Studio" },
            new String[] { "Gopal Reddy", "Reddy Construction" }, new String[] { "Anjum Ali", "Ali Travel Desk" },
            new String[] { "Bhavesh Shah", "Shah Auditors" }, new String[] { "Kritika Nair", "Nair Health Care" },
            new String[] { "Yusuf Pathan", "Pathan Security Systems" }, new String[] { "Ritu Malhotra", "Malhotra Events" },
            new String[] { "Sandeep Dixit", "Dixit Telecom" }, new String[] { "Nandini Rao", "Rao Learning" },
            new String[] { "Pawan Agarwal", "Agarwal Traders" }, new String[] { "Lata Mishra", "Mishra Facility Services" });

    private static final List<String> PURPOSES = List.of(
            "Client meeting about the Q3 proposal", "Interview for the backend developer role", "Deliver the new office stationery",
            "Annual maintenance of the air conditioners", "Vendor demo of the expense software", "Audit documents review",
            "Training session for the support team", "Courier pickup and paperwork", "Catering tasting for the town hall",
            "Legal consultation about the lease", "Equipment installation in the server room", "Candidate onboarding documents");

    private final UserRepository users;
    private final VisitRepository visits;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, VisitRepository visits, PasswordEncoder encoder) {
        this.users = users;
        this.visits = visits;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(GUARDS, Role.SECURITY_ADMIN, "guard123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (visits.count() == 0) {
            seedVisits();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private String phone(Random random) {
        return "+9198" + (10000000 + random.nextInt(89999999));
    }

    private void seedVisits() {
        Random random = new Random(4);
        LocalDateTime now = LocalDateTime.now();
        LocalDate today = now.toLocalDate();
        List<User> employees = EMPLOYEES.stream().map(e -> users.findByUsername(e[0]).orElseThrow()).toList();
        Set<String> codes = new HashSet<>(Set.of("VP-100001"));
        List<Visit> toSave = new ArrayList<>();
        int[] cursor = { 0 };

        // plan: date offset, status, hour
        // today
        add(toSave, codes, random, employees.get(0), today, LocalTime.of(15, 0), VisitStatus.EXPECTED, "VP-100001", cursor, now);
        add(toSave, codes, random, employees.get(0), today, LocalTime.of(10, 30), VisitStatus.CHECKED_IN, null, cursor, now);
        int[] todayHours = { 9, 9, 10, 11, 11, 13, 14, 16 };
        VisitStatus[] todayStatus = { VisitStatus.CHECKED_OUT, VisitStatus.CHECKED_OUT, VisitStatus.CHECKED_IN, VisitStatus.CHECKED_IN,
                VisitStatus.CHECKED_OUT, VisitStatus.EXPECTED, VisitStatus.EXPECTED, VisitStatus.CANCELLED };
        for (int i = 0; i < todayHours.length; i++) {
            add(toSave, codes, random, employees.get(1 + random.nextInt(employees.size() - 1)), today, LocalTime.of(todayHours[i], 0), todayStatus[i], null, cursor, now);
        }
        // yesterday: aarav's finished visit and two no-shows
        add(toSave, codes, random, employees.get(0), today.minusDays(1), LocalTime.of(11, 0), VisitStatus.CHECKED_OUT, null, cursor, now);
        add(toSave, codes, random, employees.get(3), today.minusDays(1), LocalTime.of(12, 0), VisitStatus.EXPECTED, null, cursor, now);
        add(toSave, codes, random, employees.get(7), today.minusDays(1), LocalTime.of(15, 0), VisitStatus.EXPECTED, null, cursor, now);
        // the last two weeks
        for (int d = 2; d <= 13; d++) {
            int count = 1 + random.nextInt(3);
            for (int k = 0; k < count; k++) {
                VisitStatus s = random.nextInt(12) == 0 ? VisitStatus.EXPECTED : random.nextInt(14) == 0 ? VisitStatus.CANCELLED : VisitStatus.CHECKED_OUT;
                add(toSave, codes, random, employees.get(1 + random.nextInt(employees.size() - 1)), today.minusDays(d), LocalTime.of(9 + random.nextInt(8), random.nextInt(2) * 30), s, null, cursor, now);
            }
        }
        // coming days
        add(toSave, codes, random, employees.get(0), today.plusDays(1), LocalTime.of(10, 0), VisitStatus.EXPECTED, null, cursor, now);
        for (int d = 1; d <= 6; d++) {
            int count = 1 + random.nextInt(2);
            for (int k = 0; k < count; k++) {
                VisitStatus s = random.nextInt(10) == 0 ? VisitStatus.CANCELLED : VisitStatus.EXPECTED;
                add(toSave, codes, random, employees.get(1 + random.nextInt(employees.size() - 1)), today.plusDays(d), LocalTime.of(9 + random.nextInt(8), random.nextInt(2) * 30), s, null, cursor, now);
            }
        }
        visits.saveAll(toSave);
    }

    private void add(List<Visit> out, Set<String> codes, Random random, User host, LocalDate date, LocalTime time, VisitStatus status,
                     String fixedCode, int[] cursor, LocalDateTime now) {
        String[] who = VISITORS.get(cursor[0]++ % VISITORS.size());
        String code = fixedCode;
        while (code == null || (fixedCode == null && !codes.add(code))) {
            code = "VP-" + (100000 + random.nextInt(900000));
        }
        Visit v = new Visit(code, who[0], phone(random), who[1], PURPOSES.get(random.nextInt(PURPOSES.size())), date, time, host);
        LocalDateTime arrivedAt = LocalDateTime.of(date, time).plusMinutes(random.nextInt(15) - 5);
        LocalDateTime in = status == VisitStatus.CHECKED_IN || status == VisitStatus.CHECKED_OUT ? arrivedAt : null;
        if (status == VisitStatus.CHECKED_IN && in.isAfter(now)) {
            in = now.minusMinutes(20);
        }
        LocalDateTime outAt = status == VisitStatus.CHECKED_OUT ? in.plusMinutes(25 + random.nextInt(90)) : null;
        if (outAt != null && outAt.isAfter(now)) { // never record a check-out in the future when the app starts early in the day
            outAt = now.minusMinutes(5 + random.nextInt(20));
            in = outAt.minusMinutes(30 + random.nextInt(40));
        }
        v.forceState(status, date.minusDays(1 + random.nextInt(3)).atTime(9, 15), in, outAt, GUARDS.get(random.nextInt(GUARDS.size()))[0]);
        out.add(v);
    }
}
