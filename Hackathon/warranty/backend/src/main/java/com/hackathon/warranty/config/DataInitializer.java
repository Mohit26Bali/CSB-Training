package com.hackathon.warranty.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.warranty.entity.Claim;
import com.hackathon.warranty.entity.ClaimPriority;
import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.ProductCategory;
import com.hackathon.warranty.entity.Role;
import com.hackathon.warranty.entity.User;
import com.hackathon.warranty.repo.ClaimRepository;
import com.hackathon.warranty.repo.ProductRepository;
import com.hackathon.warranty.repo.UserRepository;

/**
 * Seeds demo users, products (some under warranty, some about to expire, some expired) and a spread of claims.
 * Idempotent: existing users are left alone, products and claims are only created when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> AGENTS = List.of(
            new String[] { "agent", "Ramesh Pillai" },
            new String[] { "sunil.agarwal", "Sunil Agarwal" });

    private static final List<String[]> CUSTOMERS = List.of(
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

    /** name, brand, category, warranty months, serial prefix */
    private static final List<Object[]> CATALOGUE = List.of(
            new Object[] { "ThinkPad E14", "Lenovo", ProductCategory.LAPTOP, 36, "LN" },
            new Object[] { "MacBook Air M2", "Apple", ProductCategory.LAPTOP, 12, "AP" },
            new Object[] { "Inspiron 15", "Dell", ProductCategory.LAPTOP, 24, "DL" },
            new Object[] { "Galaxy S23", "Samsung", ProductCategory.MOBILE, 12, "SM" },
            new Object[] { "iPhone 14", "Apple", ProductCategory.MOBILE, 12, "AP" },
            new Object[] { "OnePlus 11", "OnePlus", ProductCategory.MOBILE, 12, "OP" },
            new Object[] { "Bravia 55 inch", "Sony", ProductCategory.TELEVISION, 24, "SY" },
            new Object[] { "Crystal 4K 50 inch", "Samsung", ProductCategory.TELEVISION, 24, "SM" },
            new Object[] { "Front Load Washer 8 kg", "LG", ProductCategory.APPLIANCE, 36, "LG" },
            new Object[] { "Double Door Fridge 260 L", "Whirlpool", ProductCategory.APPLIANCE, 60, "WP" },
            new Object[] { "Microwave Oven 28 L", "IFB", ProductCategory.APPLIANCE, 24, "IF" },
            new Object[] { "WH-1000XM5 Headphones", "Sony", ProductCategory.AUDIO, 12, "SY" },
            new Object[] { "AirPods Pro", "Apple", ProductCategory.AUDIO, 12, "AP" },
            new Object[] { "Galaxy Watch 5", "Samsung", ProductCategory.WEARABLE, 12, "SM" },
            new Object[] { "Apple Watch SE", "Apple", ProductCategory.WEARABLE, 12, "AP" });

    private static final Map<ProductCategory, List<String>> ISSUES = Map.of(
            ProductCategory.LAPTOP, List.of("The screen flickers and then goes black after a few minutes.", "The battery drains in under an hour and the laptop shuts down suddenly.", "The keyboard has stopped responding for several keys."),
            ProductCategory.MOBILE, List.of("The phone does not charge unless the cable is held at an angle.", "The touchscreen ignores touches near the bottom edge.", "The camera app crashes and the rear lens looks foggy."),
            ProductCategory.TELEVISION, List.of("There is a vertical line across the screen and the picture is distorted.", "The TV switches itself off every few minutes."),
            ProductCategory.APPLIANCE, List.of("The machine is making a loud grinding noise and leaks water.", "It is no longer cooling properly and the compressor is humming constantly.", "The display shows an error code and the door will not open."),
            ProductCategory.AUDIO, List.of("The left ear stopped working and there is crackling on the right.", "Noise cancelling has stopped working completely."),
            ProductCategory.WEARABLE, List.of("The watch will not hold a charge past lunchtime.", "The heart rate sensor shows no reading at all."),
            ProductCategory.OTHER, List.of("It stopped working one day without any warning."));

    private static final List<String> REJECTIONS = List.of(
            "Physical damage from a fall is not covered by the warranty.",
            "Liquid damage was found inside the device, which the warranty does not cover.",
            "The product shows signs of unauthorised repair.");

    private static final List<String> RESOLUTIONS = List.of(
            "Replaced the faulty part and tested for 24 hours.",
            "Motherboard replaced under warranty. Device returned to the customer.",
            "Unit replaced with a new one because the repair was not possible.");

    private final UserRepository users;
    private final ProductRepository products;
    private final ClaimRepository claims;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, ProductRepository products, ClaimRepository claims,
                           PasswordEncoder encoder) {
        this.users = users;
        this.products = products;
        this.claims = claims;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(AGENTS, Role.SERVICE_AGENT, "agent123");
        seedUsers(CUSTOMERS, Role.CUSTOMER, "customer123");
        if (products.count() == 0) {
            List<Product> seeded = seedProducts();
            if (claims.count() == 0) {
                seedClaims(seeded);
            }
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private List<Product> seedProducts() {
        Random random = new Random(7);
        LocalDate today = LocalDate.now();
        Set<String> usedSerials = new HashSet<>();
        List<Product> result = new ArrayList<>();

        for (String[] person : CUSTOMERS) {
            User owner = users.findByUsername(person[0]).orElseThrow();
            List<Object[]> picks = new ArrayList<>(CATALOGUE);
            java.util.Collections.shuffle(picks, random);
            for (Object[] item : picks.subList(0, 3)) {
                int months = (Integer) item[3];
                int roll = random.nextInt(100);
                LocalDate purchase;
                if (roll < 25) {
                    purchase = today.minusMonths(months + 1 + random.nextInt(8)).minusDays(random.nextInt(20)); // expired
                } else if (roll < 45) {
                    purchase = today.plusDays(3 + random.nextInt(25)).minusMonths(months); // expires within a month
                } else {
                    purchase = today.minusMonths(1 + random.nextInt(Math.max(1, months - 2))).minusDays(random.nextInt(20)); // covered
                }
                String serial;
                do {
                    serial = item[4] + String.valueOf(10_000_000 + random.nextInt(90_000_000));
                } while (!usedSerials.add(serial));
                result.add(new Product(owner, (String) item[0], (String) item[1], (ProductCategory) item[2], serial,
                        purchase, months));
            }
        }
        return products.saveAll(result);
    }

    private void seedClaims(List<Product> seeded) {
        Random random = new Random(11);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<String> agents = AGENTS.stream().map(a -> a[0]).toList();
        List<Claim> toSave = new ArrayList<>();

        for (Product product : seeded) {
            boolean covered = product.isWarrantyActive(today);
            if (random.nextInt(100) >= (covered ? 60 : 35)) {
                continue;
            }
            // A claim on an expired product must have been filed while it was still covered, so it is already finished.
            int roll = covered ? random.nextInt(100) : 55 + random.nextInt(45);
            ClaimStatusChoice status = roll < 30 ? ClaimStatusChoice.PENDING : roll < 45 ? ClaimStatusChoice.APPROVED
                    : roll < 65 ? ClaimStatusChoice.IN_REPAIR : roll < 90 ? ClaimStatusChoice.COMPLETED : ClaimStatusChoice.REJECTED;

            List<String> issues = ISSUES.get(product.getCategory());
            ClaimPriority priority = ClaimPriority.values()[Math.min(2, random.nextInt(4))]; // HIGH is rarer
            Claim claim = new Claim(product, product.getOwner(), issues.get(random.nextInt(issues.size())), priority);

            LocalDateTime created = now.minusDays(2 + random.nextInt(24)).withHour(9 + random.nextInt(8)).withMinute(random.nextInt(60));
            if (!covered) {
                LocalDateTime lastCoveredMoment = product.getWarrantyEnd().atTime(10, 0);
                created = created.isAfter(lastCoveredMoment) ? lastCoveredMoment.minusDays(3 + random.nextInt(10)) : created;
            }
            claim.setCreatedAt(created);

            String agent = agents.get(random.nextInt(agents.size()));
            if (status != ClaimStatusChoice.PENDING) {
                if (status == ClaimStatusChoice.REJECTED) {
                    claim.reject(agent, REJECTIONS.get(random.nextInt(REJECTIONS.size())));
                } else {
                    claim.approve(agent);
                }
                claim.setDecidedAt(min(created.plusHours(4 + random.nextInt(40)), now));
                if (status == ClaimStatusChoice.IN_REPAIR || status == ClaimStatusChoice.COMPLETED) {
                    claim.startRepair();
                    claim.setRepairStartedAt(min(claim.getDecidedAt().plusDays(1 + random.nextInt(3)), now));
                }
                if (status == ClaimStatusChoice.COMPLETED) {
                    claim.complete(RESOLUTIONS.get(random.nextInt(RESOLUTIONS.size())));
                    claim.setCompletedAt(min(claim.getRepairStartedAt().plusDays(2 + random.nextInt(6)), now));
                }
            }
            toSave.add(claim);
        }
        claims.saveAll(toSave);
    }

    private enum ClaimStatusChoice { PENDING, APPROVED, IN_REPAIR, COMPLETED, REJECTED }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
