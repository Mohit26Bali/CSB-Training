package com.hackathon.procurement.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.procurement.entity.ItemCategory;
import com.hackathon.procurement.entity.PurchaseRequest;
import com.hackathon.procurement.entity.Role;
import com.hackathon.procurement.entity.User;
import com.hackathon.procurement.repo.PurchaseRequestRepository;
import com.hackathon.procurement.repo.UserRepository;

/**
 * Seeds demo users and a believable history of purchase requests. Idempotent: existing users are left alone and
 * requests are only created when the table is empty. Spending approved this month is kept comfortably under the
 * monthly budget, so the dashboard starts healthy.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> MANAGERS = List.of(
            new String[] { "procurement", "Sunita Rao" },
            new String[] { "kiran.shah", "Kiran Shah" });

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

    /** item, category, min unit cost, max unit cost, max quantity, justification */
    private static final List<Object[]> ITEMS = List.of(
            new Object[] { "24-inch monitor", ItemCategory.HARDWARE, 9000, 14000, 4, "Dual-screen setup for the new analysts" },
            new Object[] { "Laptop, 16 GB RAM", ItemCategory.HARDWARE, 65000, 95000, 2, "Replacing a laptop that is out of warranty" },
            new Object[] { "Noise-cancelling headset", ItemCategory.HARDWARE, 4000, 9000, 6, "Calls from the open-plan floor are hard to hear" },
            new Object[] { "Backup drives", ItemCategory.HARDWARE, 3500, 6500, 5, "Weekly backups for the test environment" },
            new Object[] { "Ergonomic office chair", ItemCategory.FURNITURE, 7000, 15000, 5, "Two team members reported back pain" },
            new Object[] { "Standing desk", ItemCategory.FURNITURE, 18000, 30000, 2, "Requested after the ergonomics review" },
            new Object[] { "A4 paper, 10 reams", ItemCategory.OFFICE_SUPPLIES, 2500, 4000, 5, "The floor supply runs out every month" },
            new Object[] { "Whiteboard and markers", ItemCategory.OFFICE_SUPPLIES, 1500, 4500, 3, "New meeting room needs one" },
            new Object[] { "Design tool licence (annual)", ItemCategory.SOFTWARE, 8000, 15000, 6, "Prototyping for the redesign project" },
            new Object[] { "Project management licences", ItemCategory.SOFTWARE, 12000, 20000, 8, "Tracking sprint work across three teams" },
            new Object[] { "IDE licence (annual)", ItemCategory.SOFTWARE, 7000, 12000, 5, "Standardising tooling for the backend team" },
            new Object[] { "Team workshop facilitator", ItemCategory.SERVICES, 20000, 45000, 1, "Agile coaching day for the new squad" });

    private static final List<String> REJECTIONS = List.of(
            "Please consolidate with the existing order for this item.",
            "An equivalent item is already available in stock.",
            "Over the department's allowance. Resubmit with a lower quantity.",
            "Needs a quote from at least two vendors first.");

    private final UserRepository users;
    private final PurchaseRequestRepository requests;
    private final PasswordEncoder encoder;
    private final BigDecimal monthlyBudget;

    public DataInitializer(UserRepository users, PurchaseRequestRepository requests, PasswordEncoder encoder,
                           @Value("${app.budget.monthly-limit}") BigDecimal monthlyBudget) {
        this.users = users;
        this.requests = requests;
        this.encoder = encoder;
        this.monthlyBudget = monthlyBudget;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(MANAGERS, Role.PROCUREMENT_MANAGER, "manager123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (requests.count() == 0) {
            seedRequests();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedRequests() {
        Random random = new Random(19);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime monthStart = YearMonth.now().atDay(1).atStartOfDay();
        BigDecimal thisMonthLimit = monthlyBudget.multiply(new BigDecimal("0.7")); // keep the demo comfortably in budget
        BigDecimal thisMonthSpend = BigDecimal.ZERO;
        List<String> managers = MANAGERS.stream().map(m -> m[0]).toList();
        List<PurchaseRequest> toSave = new ArrayList<>();

        for (String[] person : EMPLOYEES) {
            User employee = users.findByUsername(person[0]).orElseThrow();
            int count = 3 + random.nextInt(3);
            for (int i = 0; i < count; i++) {
                Object[] item = ITEMS.get(random.nextInt(ITEMS.size()));
                int min = (Integer) item[2];
                int max = (Integer) item[3];
                BigDecimal unit = BigDecimal.valueOf(min + random.nextInt(max - min + 1)).setScale(2);
                int quantity = 1 + random.nextInt((Integer) item[4]);
                PurchaseRequest r = new PurchaseRequest(employee, (String) item[0], (ItemCategory) item[1], quantity, unit,
                        (String) item[5]);

                // about a third are recent and still waiting; the rest are spread over the last ~80 days
                int daysAgo = random.nextInt(100) < 35 ? random.nextInt(8) : 8 + random.nextInt(70);
                LocalDateTime submitted = now.minusDays(daysAgo).withHour(9 + random.nextInt(8)).withMinute(random.nextInt(60));
                r.setSubmittedAt(submitted.isAfter(now) ? now.minusHours(1) : submitted);

                if (daysAgo > 7 || random.nextInt(100) < 15) {
                    String manager = managers.get(random.nextInt(managers.size()));
                    LocalDateTime decided = min(r.getSubmittedAt().plusHours(6 + random.nextInt(60)), now);
                    boolean approve = random.nextInt(100) < 75;
                    if (approve && !decided.isBefore(monthStart)
                            && thisMonthSpend.add(r.getEstimatedTotal()).compareTo(thisMonthLimit) > 0) {
                        approve = false; // would push this month's demo spend over the cap: reject instead
                    }
                    if (!approve) {
                        r.reject(manager, REJECTIONS.get(random.nextInt(REJECTIONS.size())));
                    } else {
                        r.approve(manager);
                        if (!decided.isBefore(monthStart)) {
                            thisMonthSpend = thisMonthSpend.add(r.getEstimatedTotal());
                        }
                        int stage = random.nextInt(100); // approved, ordered or delivered, depending on age
                        if (daysAgo > 14 && stage < 70) {
                            r.markOrdered("PO-" + (10000 + random.nextInt(90000)));
                            r.setOrderedAt(min(decided.plusDays(1 + random.nextInt(3)), now));
                            if (stage < 45) {
                                r.markDelivered();
                                r.setDeliveredAt(min(r.getOrderedAt().plusDays(2 + random.nextInt(8)), now));
                            }
                        } else if (daysAgo > 7 && stage < 30) {
                            r.markOrdered("PO-" + (10000 + random.nextInt(90000)));
                            r.setOrderedAt(min(decided.plusDays(1), now));
                        }
                    }
                    r.setDecidedAt(decided);
                }
                toSave.add(r);
            }
        }
        requests.saveAll(toSave);
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
