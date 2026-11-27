package com.hackathon.asset.config;

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

import com.hackathon.asset.entity.Asset;
import com.hackathon.asset.entity.AssetCategory;
import com.hackathon.asset.entity.AssetCondition;
import com.hackathon.asset.entity.AssetStatus;
import com.hackathon.asset.entity.Assignment;
import com.hackathon.asset.entity.AssignmentStatus;
import com.hackathon.asset.entity.Role;
import com.hackathon.asset.entity.User;
import com.hackathon.asset.repo.AssetRepository;
import com.hackathon.asset.repo.AssignmentRepository;
import com.hackathon.asset.repo.UserRepository;

/**
 * Seeds two asset admins, 30 employees and 56 assets (laptops, monitors, phones, tablets, accessories and other kit). About half are with
 * employees (nobody holds more than four, a few are overdue), some have a return request waiting, a few are in repair or retired, and
 * many have earlier returned assignments, so every history has something in it. aarav.sharma holds a MacBook Pro and a monitor, has asked
 * to return a headset, and once returned a Dell laptop. Idempotent: users only when missing, assets only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "assetadmin", "Pooja Nair" },
            new String[] { "it.desk", "Imran Sheikh" });

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

    /** category, tag prefix, how many, then name / brand / model / price per variety */
    private record Kind(AssetCategory category, String prefix, int count, String[][] varieties) {
    }

    private static final List<Kind> KINDS = List.of(
            new Kind(AssetCategory.LAPTOP, "LT", 18, new String[][] {
                    { "Apple MacBook Pro 14", "Apple", "MacBook Pro 14 M3", "189000" }, { "Dell Latitude 5440", "Dell", "Latitude 5440", "78000" },
                    { "HP EliteBook 840 G10", "HP", "EliteBook 840 G10", "92000" }, { "Lenovo ThinkPad T14 Gen 4", "Lenovo", "ThinkPad T14", "88000" },
                    { "Apple MacBook Air M2", "Apple", "MacBook Air M2", "112000" } }),
            new Kind(AssetCategory.MONITOR, "MN", 12, new String[][] {
                    { "Dell UltraSharp 27 4K", "Dell", "U2723QE", "42000" }, { "LG 27 inch 4K", "LG", "27UP850", "34000" },
                    { "Samsung 24 inch FHD", "Samsung", "LF24T350", "11500" } }),
            new Kind(AssetCategory.PHONE, "PH", 6, new String[][] {
                    { "iPhone 14", "Apple", "iPhone 14", "69900" }, { "Galaxy S23", "Samsung", "SM-S911B", "74999" }, { "Pixel 8", "Google", "Pixel 8", "75999" } }),
            new Kind(AssetCategory.TABLET, "TB", 4, new String[][] {
                    { "iPad 10th generation", "Apple", "iPad 10", "44900" }, { "Galaxy Tab S9", "Samsung", "SM-X710", "72999" } }),
            new Kind(AssetCategory.ACCESSORY, "AC", 12, new String[][] {
                    { "Jabra Evolve2 65 headset", "Jabra", "Evolve2 65", "18500" }, { "Logitech MX Master 3S mouse", "Logitech", "MX Master 3S", "9995" },
                    { "Dell WD19S docking station", "Dell", "WD19S", "16500" }, { "Logitech Brio webcam", "Logitech", "Brio 4K", "14995" } }),
            new Kind(AssetCategory.OTHER, "OT", 4, new String[][] {
                    { "Epson projector", "Epson", "EB-X06", "36000" }, { "HP LaserJet printer", "HP", "M404dn", "24500" }, { "Cisco IP phone", "Cisco", "CP-8851", "15500" } }));

    private static final List<String> RETURN_NOTES = List.of("Upgrading to a new one", "Project finished", "Leaving the team", "Screen flickers, please check", "Not needed any more");
    private static final List<String> RECEIVE_NOTES = List.of("Cleaned and checked", "Charger returned too", "Small scratch on the lid", "Everything in order", "Battery health is low");

    private final UserRepository users;
    private final AssetRepository assets;
    private final AssignmentRepository assignments;
    private final PasswordEncoder encoder;

    public DataInitializer(UserRepository users, AssetRepository assets, AssignmentRepository assignments, PasswordEncoder encoder) {
        this.users = users;
        this.assets = assets;
        this.assignments = assignments;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.ASSET_ADMIN, "admin123");
        seedUsers(EMPLOYEES, Role.EMPLOYEE, "employee123");
        if (assets.count() == 0) {
            seedAssets();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private static AssetCondition pick(Random random) {
        int r = random.nextInt(100);
        return r < 20 ? AssetCondition.NEW : r < 70 ? AssetCondition.GOOD : r < 92 ? AssetCondition.FAIR : AssetCondition.POOR;
    }

    private void seedAssets() {
        Random random = new Random(15);
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<User> employees = users.findAll().stream().filter(u -> u.getRole() == Role.EMPLOYEE).sorted((a, b) -> a.getUsername().compareTo(b.getUsername())).toList();
        User aarav = employees.get(0);

        List<Asset> all = new ArrayList<>();
        int serial = 0;
        for (Kind kind : KINDS) {
            for (int i = 0; i < kind.count(); i++) {
                String[] v = kind.varieties()[i % kind.varieties().length];
                String tag = kind.prefix() + "-" + (1001 + i);
                String sn = kind.prefix() + String.format("%08X", 0x3A5F10 + (serial++ * 7919));
                LocalDate bought = today.minusDays(60 + random.nextInt(1300));
                all.add(new Asset(tag, sn, v[0], kind.category(), v[1], v[2], bought, new BigDecimal(v[3]), pick(random), null));
            }
        }
        assets.saveAll(all);

        Map<Long, Integer> held = new HashMap<>();
        List<Assignment> made = new ArrayList<>();
        Asset macbook = all.stream().filter(a -> a.getName().startsWith("Apple MacBook Pro")).findFirst().orElseThrow();
        Asset dell = all.stream().filter(a -> a.getCategory() == AssetCategory.LAPTOP && a.getBrand().equals("Dell")).findFirst().orElseThrow();
        Asset monitor = all.stream().filter(a -> a.getCategory() == AssetCategory.MONITOR).findFirst().orElseThrow();
        Asset headset = all.stream().filter(a -> a.getName().startsWith("Jabra")).findFirst().orElseThrow();

        past(made, dell, aarav, now.minusDays(420), now.minusDays(150), random);
        dell.setStatus(AssetStatus.AVAILABLE);
        current(made, macbook, aarav, now.minusDays(140), today.plusDays(40), AssignmentStatus.ASSIGNED, random);
        current(made, monitor, aarav, now.minusDays(138), null, AssignmentStatus.ASSIGNED, random);
        current(made, headset, aarav, now.minusDays(200), null, AssignmentStatus.RETURN_REQUESTED, random);
        held.put(aarav.getId(), 3);

        int requests = 3;
        int repair = 3;
        int retired = 3;
        for (Asset asset : all) {
            if (asset == macbook || asset == dell || asset == monitor || asset == headset) {
                continue;
            }
            int roll = random.nextInt(100);
            if (retired > 0 && roll < 6) {
                retired--;
                past(made, asset, employees.get(1 + random.nextInt(employees.size() - 1)), now.minusDays(700), now.minusDays(500), random);
                asset.setStatus(AssetStatus.RETIRED);
            } else if (repair > 0 && roll < 12) {
                repair--;
                past(made, asset, employees.get(1 + random.nextInt(employees.size() - 1)), now.minusDays(400), now.minusDays(30), random);
                asset.setStatus(AssetStatus.IN_REPAIR);
            } else if (roll < 66) {
                User who = employees.get(1 + random.nextInt(employees.size() - 1));
                if (held.getOrDefault(who.getId(), 0) >= 4) {
                    continue;
                }
                held.merge(who.getId(), 1, Integer::sum);
                if (random.nextInt(3) == 0) {
                    past(made, asset, employees.get(1 + random.nextInt(employees.size() - 1)), now.minusDays(600), now.minusDays(260), random);
                }
                boolean ask = requests > 0 && roll < 22;
                if (ask) {
                    requests--;
                }
                LocalDate due = asset.getCategory() == AssetCategory.LAPTOP && random.nextInt(4) == 0 ? today.plusDays(random.nextInt(120) - 20) : null;
                current(made, asset, who, now.minusDays(20 + random.nextInt(220)), due, ask ? AssignmentStatus.RETURN_REQUESTED : AssignmentStatus.ASSIGNED, random);
            } else if (random.nextInt(3) == 0) {
                past(made, asset, employees.get(1 + random.nextInt(employees.size() - 1)), now.minusDays(380), now.minusDays(40 + random.nextInt(60)), random);
            }
        }
        assignments.saveAll(made);
        assets.saveAll(all);
    }

    /** An assignment that was handed back and accepted. */
    private void past(List<Assignment> made, Asset asset, User who, LocalDateTime from, LocalDateTime to, Random random) {
        Assignment a = new Assignment(asset, who, "assetadmin", null, null);
        AssetCondition back = random.nextInt(5) == 0 ? AssetCondition.FAIR : AssetCondition.GOOD;
        a.forceState(from, AssignmentStatus.RETURNED, to.minusDays(2), RETURN_NOTES.get(random.nextInt(RETURN_NOTES.size())), to, "it.desk", back,
                RECEIVE_NOTES.get(random.nextInt(RECEIVE_NOTES.size())));
        made.add(a);
    }

    /** An assignment that is still open; the asset's status follows it. */
    private void current(List<Assignment> made, Asset asset, User who, LocalDateTime from, LocalDate due, AssignmentStatus status, Random random) {
        Assignment a = new Assignment(asset, who, "assetadmin", due, null);
        if (status == AssignmentStatus.RETURN_REQUESTED) {
            a.forceState(from, status, LocalDateTime.now().minusDays(1 + random.nextInt(8)), RETURN_NOTES.get(random.nextInt(RETURN_NOTES.size())), null, null, null, null);
            asset.setStatus(AssetStatus.RETURN_REQUESTED);
        } else {
            a.forceState(from, status, null, null, null, null, null, null);
            asset.setStatus(AssetStatus.ASSIGNED);
        }
        made.add(a);
    }
}
