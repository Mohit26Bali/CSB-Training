package com.hackathon.expense.config;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.expense.entity.Expense;
import com.hackathon.expense.entity.ExpenseCategory;
import com.hackathon.expense.entity.ExpenseStatus;
import com.hackathon.expense.entity.Role;
import com.hackathon.expense.entity.User;
import com.hackathon.expense.repo.ExpenseRepository;
import com.hackathon.expense.repo.UserRepository;

/**
 * Seeds demo users and a believable history of expenses. Idempotent: users that already exist are left alone,
 * and expenses are only created when the table is empty.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final String MANAGER_PASSWORD = "manager123";
    private static final String EMPLOYEE_PASSWORD = "employee123";

    /** username, full name */
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
            new String[] { "saanvi.rao", "Saanvi Rao" },
            new String[] { "aditya.singh", "Aditya Singh" },
            new String[] { "karan.malhotra", "Karan Malhotra" });

    /** description, min amount, max amount, per category */
    private static final Map<ExpenseCategory, List<Object[]>> TEMPLATES = Map.of(
            ExpenseCategory.TRAVEL, List.of(
                    new Object[] { "Flight to Bengaluru for client workshop", 5200, 9800 },
                    new Object[] { "Cab fares for site visit", 600, 2400 },
                    new Object[] { "Train ticket to Pune, project review", 900, 2100 }),
            ExpenseCategory.MEALS, List.of(
                    new Object[] { "Team lunch after release", 1800, 6400 },
                    new Object[] { "Client dinner, quarterly review", 3500, 9000 },
                    new Object[] { "Working lunch during on-site support", 350, 900 }),
            ExpenseCategory.ACCOMMODATION, List.of(
                    new Object[] { "Hotel, 2 nights, Hyderabad conference", 7200, 15500 },
                    new Object[] { "Hotel stay during client onboarding", 4800, 11000 }),
            ExpenseCategory.OFFICE_SUPPLIES, List.of(
                    new Object[] { "Whiteboard markers and sticky notes", 250, 900 },
                    new Object[] { "Ergonomic keyboard and mouse", 2200, 5200 },
                    new Object[] { "Printer cartridges for the floor", 1800, 4400 }),
            ExpenseCategory.SOFTWARE, List.of(
                    new Object[] { "Annual licence, design tool", 6500, 14000 },
                    new Object[] { "Monthly cloud hosting for demo environment", 1500, 5200 }),
            ExpenseCategory.TRAINING, List.of(
                    new Object[] { "Online course, cloud certification", 3200, 8800 },
                    new Object[] { "Workshop fee, agile coaching", 4000, 12000 }),
            ExpenseCategory.OTHER, List.of(
                    new Object[] { "Courier charges for signed contracts", 200, 900 },
                    new Object[] { "Visa photographs and stationery", 300, 1100 }));

    private static final List<String> REJECTION_REASONS = List.of(
            "Receipt is missing or unreadable. Please resubmit with a clear copy.",
            "Amount is above the policy limit for this category.",
            "This looks like a duplicate of an earlier claim.",
            "Personal expense, not reimbursable under company policy.",
            "Needs prior approval before purchase. Please raise a procurement request.");

    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository, ExpenseRepository expenseRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(MANAGERS, Role.MANAGER, MANAGER_PASSWORD);
        seedUsers(EMPLOYEES, Role.EMPLOYEE, EMPLOYEE_PASSWORD);
        if (expenseRepository.count() == 0) {
            seedExpenses();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = passwordEncoder.encode(password); // one BCrypt run shared by everyone with this demo password
        List<User> missing = people.stream()
                .filter(p -> !userRepository.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role))
                .toList();
        userRepository.saveAll(missing);
    }

    private void seedExpenses() {
        Random random = new Random(7); // fixed seed: the same demo data on every fresh database
        List<User> employees = EMPLOYEES.stream().map(e -> userRepository.findByUsername(e[0]).orElseThrow()).toList();
        List<String> managers = MANAGERS.stream().map(m -> m[0]).toList();
        ExpenseCategory[] categories = ExpenseCategory.values();
        LocalDateTime now = LocalDateTime.now();
        List<Expense> expenses = new ArrayList<>();

        for (User employee : employees) {
            int count = 3 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                ExpenseCategory category = categories[random.nextInt(categories.length)];
                List<Object[]> options = TEMPLATES.get(category);
                Object[] template = options.get(random.nextInt(options.size()));
                int min = (Integer) template[1];
                int max = (Integer) template[2];
                BigDecimal amount = BigDecimal.valueOf(min + random.nextInt(max - min + 1)).setScale(2);

                // about a quarter of the claims are from the last 10 days, so the manager's queue is never empty
                int daysAgo = random.nextInt(100) < 25 ? 1 + random.nextInt(10) : 11 + random.nextInt(65);
                LocalDate date = LocalDate.now().minusDays(daysAgo);
                Expense expense = new Expense(employee, date, category, amount, (String) template[0]);
                LocalDateTime submitted = date.atTime(9 + random.nextInt(9), random.nextInt(60))
                        .plusDays(random.nextInt(2));
                expense.setSubmittedAt(submitted.isAfter(now) ? now.minusHours(1) : submitted);

                // Expenses from the last ten days are mostly still waiting; older ones have been decided.
                boolean decided = daysAgo > 10 || random.nextInt(100) < 12;
                if (decided) {
                    String manager = managers.get(random.nextInt(managers.size()));
                    boolean approve = random.nextInt(100) < 72;
                    expense.decide(approve ? ExpenseStatus.APPROVED : ExpenseStatus.REJECTED, manager,
                            approve ? null : REJECTION_REASONS.get(random.nextInt(REJECTION_REASONS.size())));
                    LocalDateTime decidedAt = expense.getSubmittedAt().plusHours(6 + random.nextInt(72));
                    expense.setDecidedAt(decidedAt.isAfter(now) ? now : decidedAt);
                }
                expenses.add(expense);
            }
        }
        expenseRepository.saveAll(expenses);
    }
}
