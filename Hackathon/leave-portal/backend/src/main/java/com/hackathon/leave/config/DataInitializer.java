package com.hackathon.leave.config;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.leave.entity.LeaveBalance;
import com.hackathon.leave.entity.LeaveRequest;
import com.hackathon.leave.entity.LeaveStatus;
import com.hackathon.leave.entity.LeaveType;
import com.hackathon.leave.entity.Role;
import com.hackathon.leave.entity.User;
import com.hackathon.leave.repo.LeaveBalanceRepository;
import com.hackathon.leave.repo.LeaveRequestRepository;
import com.hackathon.leave.repo.UserRepository;
import com.hackathon.leave.service.LeaveService;

/**
 * Seeds demo users and a believable year of leave. Idempotent: existing users are left alone and leave data is
 * only created when there are no leave requests yet. Balances are derived from the approved requests, so they
 * always add up.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> MANAGERS = List.of(
            new String[] { "manager", "Rajesh Kumar" },
            new String[] { "neha.kapoor", "Neha Kapoor" });

    private static final List<String[]> EMPLOYEES = List.of(
            new String[] { "aarav.sharma", "Aarav Sharma" },
            new String[] { "diya.patel", "Diya Patel" },
            new String[] { "rohan.mehta", "Rohan Mehta" },
            new String[] { "ananya.iyer", "Ananya Iyer" },
            new String[] { "vivaan.gupta", "Vivaan Gupta" },
            new String[] { "kavya.nair", "Kavya Nair" },
            new String[] { "arjun.reddy", "Arjun Reddy" },
            new String[] { "meera.joshi", "Meera Joshi" },
            new String[] { "ishaan.verma", "Ishaan Verma" },
            new String[] { "saanvi.rao", "Saanvi Rao" });

    private static final Map<LeaveType, List<String>> REASONS = Map.of(
            LeaveType.ANNUAL, List.of("Family trip", "Cousin's wedding", "Long weekend away", "Visiting parents", "Festival break"),
            LeaveType.SICK, List.of("Fever and cold", "Dental procedure", "Migraine", "Doctor's appointment"),
            LeaveType.CASUAL, List.of("Bank and passport work", "Moving house", "Child's school event"),
            LeaveType.UNPAID, List.of("Extended personal break"));

    private static final List<String> REJECTION_NOTES = List.of(
            "The team is short-staffed that week. Could you pick other dates?",
            "Clashes with the release freeze.",
            "Please coordinate with your project lead first.");

    private final UserRepository users;
    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, LeaveRequestRepository requests, LeaveBalanceRepository balances,
                           PasswordEncoder encoder) {
        this.users = users;
        this.requests = requests;
        this.balances = balances;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(MANAGERS, Role.MANAGER, "manager123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (requests.count() == 0) {
            seedLeave();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedLeave() {
        Random random = new Random(12);
        LocalDate today = LocalDate.now();
        int year = today.getYear();
        LocalDateTime now = LocalDateTime.now();
        List<User> everyone = new ArrayList<>();
        MANAGERS.forEach(m -> everyone.add(users.findByUsername(m[0]).orElseThrow()));
        EMPLOYEES.forEach(e -> everyone.add(users.findByUsername(e[0]).orElseThrow()));
        List<String> managerNames = MANAGERS.stream().map(m -> m[0]).toList();
        List<LeaveRequest> toSave = new ArrayList<>();

        for (User person : everyone) {
            Map<LeaveType, Integer> used = new EnumMap<>(LeaveType.class);
            Map<LeaveType, Integer> reserved = new EnumMap<>(LeaveType.class);
            LocalDate cursor = LocalDate.of(year, 1, 1).plusDays(15 + random.nextInt(60));
            int count = 3 + random.nextInt(3);

            for (int i = 0; i < count; i++) {
                LeaveType type = pickType(random);
                LocalDate start = nextWeekday(cursor);
                LocalDate end = start.plusDays(random.nextInt(5));
                if (end.getYear() != year) {
                    break;
                }
                int days = LeaveService.workingDays(start, end);
                cursor = end.plusDays(12 + random.nextInt(40));
                if (days <= 0) {
                    continue;
                }

                List<String> reasons = REASONS.get(type);
                LeaveRequest request = new LeaveRequest(person, type, start, end, days,
                        reasons.get(random.nextInt(reasons.size())));
                LocalDateTime submitted = start.minusDays(3 + random.nextInt(18)).atTime(9 + random.nextInt(8), random.nextInt(60));
                request.setSubmittedAt(submitted.isAfter(now) ? now.minusHours(2) : submitted);
                int allowance = type.getYearlyAllowance();

                if (end.isBefore(today) || !start.isAfter(today)) {
                    // already happened (or under way): approved, unless the balance would not have covered it
                    boolean enough = !type.isTracked() || used.getOrDefault(type, 0) + days <= allowance;
                    boolean approve = enough && random.nextInt(100) < 85;
                    String manager = pickManager(managerNames, person, random);
                    request.decide(approve ? LeaveStatus.APPROVED : LeaveStatus.REJECTED, manager,
                            approve ? null : (enough ? REJECTION_NOTES.get(random.nextInt(REJECTION_NOTES.size()))
                                    : "Not enough leave balance for this request."));
                    request.setDecidedAt(min(request.getSubmittedAt().plusHours(5 + random.nextInt(48)), now));
                    if (approve && type.isTracked()) {
                        used.merge(type, days, Integer::sum);
                    }
                } else if (random.nextInt(100) < 55) {
                    // in the future and still waiting for a manager
                    if (type.isTracked() && used.getOrDefault(type, 0) + reserved.getOrDefault(type, 0) + days > allowance) {
                        continue;
                    }
                    reserved.merge(type, days, Integer::sum);
                } else {
                    boolean enough = !type.isTracked() || used.getOrDefault(type, 0) + days <= allowance;
                    if (!enough) {
                        continue;
                    }
                    request.decide(LeaveStatus.APPROVED, pickManager(managerNames, person, random), "Enjoy your break!");
                    request.setDecidedAt(min(request.getSubmittedAt().plusHours(8 + random.nextInt(30)), now));
                    if (type.isTracked()) {
                        used.merge(type, days, Integer::sum);
                    }
                }
                toSave.add(request);
            }

            // Upcoming leave: most people have something planned in the next weeks, and many are still waiting.
            if (random.nextInt(100) < 70) {
                LocalDate start = nextWeekday(today.plusDays(7 + random.nextInt(40)));
                LocalDate end = start.plusDays(random.nextInt(4));
                LeaveType type = random.nextBoolean() ? LeaveType.ANNUAL : LeaveType.CASUAL;
                int days = LeaveService.workingDays(start, end);
                boolean clashes = toSave.stream().anyMatch(r -> r.getEmployee() == person && r.overlaps(start, end));
                boolean fits = used.getOrDefault(type, 0) + reserved.getOrDefault(type, 0) + days <= type.getYearlyAllowance();
                if (end.getYear() == year && days > 0 && fits && !clashes) {
                    List<String> reasons = REASONS.get(type);
                    LeaveRequest upcoming = new LeaveRequest(person, type, start, end, days,
                            reasons.get(random.nextInt(reasons.size())));
                    upcoming.setSubmittedAt(now.minusDays(1 + random.nextInt(5)));
                    reserved.merge(type, days, Integer::sum);
                    toSave.add(upcoming);
                }
            }

            for (LeaveType type : LeaveType.values()) {
                LeaveBalance balance = new LeaveBalance(person, year, type, type.getYearlyAllowance());
                balance.setUsed(used.getOrDefault(type, 0));
                balances.save(balance);
            }
        }
        requests.saveAll(toSave);
    }

    private static LeaveType pickType(Random random) {
        int roll = random.nextInt(100);
        return roll < 50 ? LeaveType.ANNUAL : roll < 75 ? LeaveType.SICK : LeaveType.CASUAL;
    }

    private static LocalDate nextWeekday(LocalDate date) {
        LocalDate d = date;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** A manager other than the person themselves (managers cannot decide their own leave). */
    private static String pickManager(List<String> managers, User person, Random random) {
        List<String> others = managers.stream().filter(m -> !m.equals(person.getUsername())).toList();
        return others.get(random.nextInt(others.size()));
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
