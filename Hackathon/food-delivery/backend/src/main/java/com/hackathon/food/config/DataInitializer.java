package com.hackathon.food.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.food.entity.FoodOrder;
import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.entity.MenuItem;
import com.hackathon.food.entity.OrderStatus;
import com.hackathon.food.entity.Role;
import com.hackathon.food.entity.User;
import com.hackathon.food.repo.FoodOrderRepository;
import com.hackathon.food.repo.MenuItemRepository;
import com.hackathon.food.repo.UserRepository;

/**
 * Seeds two restaurant admins, 30 customers, a 28-dish menu (one dish sold out) and about 150 orders: three weeks of delivered orders with a few
 * cancellations, and ten orders in the last three hours covering every status, so the kitchen board and the dashboard have something to show.
 * aarav.sharma has two delivered orders, a cancelled one and an order being prepared right now. Idempotent: users only when missing,
 * menu and orders only when there are none.
 */
@Component
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final List<String[]> ADMINS = List.of(
            new String[] { "restaurant", "Chef Rajan Pillai" },
            new String[] { "kitchen", "Meena Kulkarni" });

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

    /** name, description, category, price, vegetarian, active */
    private static final List<Object[]> MENU = List.of(
            new Object[] { "Paneer Tikka", "Cottage cheese marinated in yoghurt and spices, grilled in the tandoor.", MenuCategory.STARTERS, 280, true, true },
            new Object[] { "Chicken 65", "Crisp, fiery South Indian fried chicken with curry leaves.", MenuCategory.STARTERS, 260, false, true },
            new Object[] { "Veg Spring Rolls", "Crunchy rolls stuffed with seasoned vegetables, with sweet chilli sauce.", MenuCategory.STARTERS, 190, true, true },
            new Object[] { "Hara Bhara Kebab", "Spinach and green pea patties, shallow fried.", MenuCategory.STARTERS, 240, true, true },
            new Object[] { "Fish Amritsari", "Gram flour battered fish fried golden, with mint chutney.", MenuCategory.STARTERS, 320, false, true },
            new Object[] { "Crispy Corn", "Corn kernels tossed in a spiced crunchy coating.", MenuCategory.STARTERS, 210, true, true },
            new Object[] { "Butter Chicken", "Tandoori chicken in a silky tomato and butter gravy.", MenuCategory.MAINS, 360, false, true },
            new Object[] { "Paneer Butter Masala", "Soft paneer in a rich, mildly sweet tomato gravy.", MenuCategory.MAINS, 310, true, true },
            new Object[] { "Dal Makhani", "Black lentils simmered overnight with butter and cream.", MenuCategory.MAINS, 260, true, true },
            new Object[] { "Chicken Biryani", "Fragrant basmati layered with marinated chicken, served with raita.", MenuCategory.MAINS, 340, false, true },
            new Object[] { "Veg Biryani", "Basmati rice cooked with vegetables and whole spices, served with raita.", MenuCategory.MAINS, 270, true, true },
            new Object[] { "Mutton Rogan Josh", "Slow-cooked Kashmiri mutton curry. Sold out today.", MenuCategory.MAINS, 420, false, false },
            new Object[] { "Palak Paneer", "Paneer cubes in a smooth spinach gravy.", MenuCategory.MAINS, 290, true, true },
            new Object[] { "Chole Bhature", "Spiced chickpea curry with two fluffy fried breads.", MenuCategory.MAINS, 190, true, true },
            new Object[] { "Butter Naan", "Soft leavened bread brushed with butter.", MenuCategory.BREADS_AND_RICE, 60, true, true },
            new Object[] { "Garlic Naan", "Naan topped with garlic and coriander.", MenuCategory.BREADS_AND_RICE, 70, true, true },
            new Object[] { "Tandoori Roti", "Whole wheat bread baked in the tandoor.", MenuCategory.BREADS_AND_RICE, 30, true, true },
            new Object[] { "Jeera Rice", "Basmati rice tempered with cumin.", MenuCategory.BREADS_AND_RICE, 140, true, true },
            new Object[] { "Steamed Rice", "Plain basmati rice.", MenuCategory.BREADS_AND_RICE, 110, true, true },
            new Object[] { "Gulab Jamun", "Two warm milk dumplings in saffron syrup.", MenuCategory.DESSERTS, 110, true, true },
            new Object[] { "Rasmalai", "Soft cottage cheese discs in chilled, cardamom-scented milk.", MenuCategory.DESSERTS, 140, true, true },
            new Object[] { "Kulfi Falooda", "Pistachio kulfi with vermicelli, rose syrup and basil seeds.", MenuCategory.DESSERTS, 160, true, true },
            new Object[] { "Brownie with Ice Cream", "Warm chocolate brownie with a scoop of vanilla ice cream.", MenuCategory.DESSERTS, 190, true, true },
            new Object[] { "Masala Chai", "Strong tea simmered with ginger and cardamom.", MenuCategory.DRINKS, 50, true, true },
            new Object[] { "Sweet Lassi", "Thick chilled yoghurt drink, lightly sweetened.", MenuCategory.DRINKS, 90, true, true },
            new Object[] { "Fresh Lime Soda", "Lime, soda and a pinch of black salt. Sweet or salted.", MenuCategory.DRINKS, 80, true, true },
            new Object[] { "Cold Coffee", "Blended coffee with milk and ice cream.", MenuCategory.DRINKS, 120, true, true },
            new Object[] { "Mango Shake", "Alphonso mango blended with chilled milk.", MenuCategory.DRINKS, 130, true, true });

    private static final List<String> ADDRESSES = List.of(
            "Flat 302, Green Park Apartments, Baner, Pune", "12, Lotus Society, Kothrud, Pune", "B-704, Sky Heights, Viman Nagar, Pune",
            "House 45, Lane 6, Koregaon Park, Pune", "Flat 11, Shree Residency, Aundh, Pune", "A-1203, Palm Court, Hinjewadi Phase 1, Pune",
            "9, Rose Villa, Kalyani Nagar, Pune", "Flat 508, Orchid Towers, Wakad, Pune");
    private static final List<String> NOTES = List.of("Less spicy please", "Ring the bell twice", "No onions in the biryani", "Leave at the gate", "Extra raita");
    private static final List<String> REASONS = List.of("Ordered by mistake", "Changed my mind", "Delivery address was wrong", "Kitchen is out of an item");

    private final UserRepository users;
    private final MenuItemRepository menu;
    private final FoodOrderRepository orders;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public DataInitializer(UserRepository users, MenuItemRepository menu, FoodOrderRepository orders, PasswordEncoder encoder, Clock clock) {
        this.clock = clock;
        this.users = users;
        this.menu = menu;
        this.orders = orders;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        seedUsers(ADMINS, Role.RESTAURANT_ADMIN, "admin123");
        seedUsers(CUSTOMERS, Role.CUSTOMER, "customer123");
        if (menu.count() == 0) {
            menu.saveAll(MENU.stream().map(m -> new MenuItem((String) m[0], (String) m[1], (MenuCategory) m[2], BigDecimal.valueOf((Integer) m[3]),
                    (Boolean) m[4], (Boolean) m[5])).toList());
        }
        if (orders.count() == 0) {
            seedOrders();
        }
    }

    private void seedUsers(List<String[]> people, Role role, String password) {
        String hash = encoder.encode(password);
        users.saveAll(people.stream().filter(p -> !users.existsByUsername(p[0]))
                .map(p -> new User(p[0], hash, p[1], role)).toList());
    }

    private void seedOrders() {
        Random random = new Random(1);
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = now.toLocalDate();
        List<MenuItem> dishes = menu.findAllByOrderByCategoryAscNameAsc().stream().filter(MenuItem::isActive).toList();
        List<User> customers = users.findAll().stream().filter(u -> u.getRole() == Role.CUSTOMER).sorted((a, b) -> a.getUsername().compareTo(b.getUsername())).toList();
        User aarav = customers.get(0);
        List<FoodOrder> made = new ArrayList<>();

        made.add(order(aarav, pick(dishes, random, "Butter Chicken", "Butter Naan", "Sweet Lassi"), now.minusDays(8).withHour(20).withMinute(15), OrderStatus.DELIVERED, "restaurant", null, random));
        made.add(order(aarav, pick(dishes, random, "Chicken Biryani", "Gulab Jamun"), now.minusDays(15).withHour(13).withMinute(5), OrderStatus.DELIVERED, "kitchen", null, random));
        made.add(order(aarav, pick(dishes, random, "Paneer Tikka", "Dal Makhani"), now.minusDays(3).withHour(21).withMinute(40), OrderStatus.CANCELLED, "aarav.sharma", "Ordered by mistake", random));
        made.add(order(aarav, pick(dishes, random, "Veg Biryani", "Masala Chai", "Rasmalai"), now.minusMinutes(25), OrderStatus.PREPARING, "restaurant", null, random));

        for (int back = 20; back >= 1; back--) {
            LocalDate day = today.minusDays(back);
            int count = 4 + random.nextInt(5) + (day.getDayOfWeek().getValue() >= 5 ? 3 : 0);
            for (int i = 0; i < count; i++) {
                LocalDateTime placed = day.atTime(11 + random.nextInt(11), random.nextInt(60));
                boolean cancelled = random.nextInt(100) < 7;
                User who = customers.get(1 + random.nextInt(customers.size() - 1));
                made.add(order(who, randomDishes(dishes, random), placed, cancelled ? OrderStatus.CANCELLED : OrderStatus.DELIVERED,
                        cancelled && random.nextBoolean() ? who.getUsername() : "restaurant", cancelled ? REASONS.get(random.nextInt(REASONS.size())) : null, random));
            }
        }

        int[] minutesAgo = { 170, 140, 115, 90, 70, 50, 35, 22, 12, 4 };
        OrderStatus[] states = { OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.DELIVERED, OrderStatus.READY, OrderStatus.PREPARING,
                OrderStatus.PREPARING, OrderStatus.ACCEPTED, OrderStatus.PLACED, OrderStatus.PLACED, OrderStatus.PLACED };
        for (int i = 0; i < minutesAgo.length; i++) {
            made.add(order(customers.get(1 + random.nextInt(customers.size() - 1)), randomDishes(dishes, random), now.minusMinutes(minutesAgo[i]), states[i], "kitchen", null, random));
        }
        orders.saveAll(made);
    }

    private static List<Object[]> pick(List<MenuItem> dishes, Random random, String... names) {
        List<Object[]> lines = new ArrayList<>();
        for (String name : names) {
            lines.add(new Object[] { dishes.stream().filter(d -> d.getName().equals(name)).findFirst().orElseThrow(), 1 + random.nextInt(2) });
        }
        return lines;
    }

    private static List<Object[]> randomDishes(List<MenuItem> dishes, Random random) {
        List<MenuItem> shuffled = new ArrayList<>(dishes);
        Collections.shuffle(shuffled, random);
        List<Object[]> lines = new ArrayList<>();
        int count = 1 + random.nextInt(4);
        for (int i = 0; i < count; i++) {
            lines.add(new Object[] { shuffled.get(i), 1 + random.nextInt(3) });
        }
        // the real service refuses orders under 100, so the demo data does not contain any
        while (lines.stream().map(l -> ((MenuItem) l[0]).getPrice().multiply(BigDecimal.valueOf((Integer) l[1]))).reduce(BigDecimal.ZERO, BigDecimal::add).compareTo(BigDecimal.valueOf(100)) < 0) {
            Object[] first = lines.get(0);
            first[1] = (Integer) first[1] + 1;
        }
        return lines;
    }

    /** Builds an order with the same price rules the service applies. */
    private FoodOrder order(User customer, List<Object[]> lines, LocalDateTime placedAt, OrderStatus status, String by, String reason, Random random) {
        BigDecimal subtotal = lines.stream().map(l -> ((MenuItem) l[0]).getPrice().multiply(BigDecimal.valueOf((Integer) l[1]))).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tax = subtotal.multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = subtotal.compareTo(new BigDecimal("500")) >= 0 ? BigDecimal.ZERO.setScale(2) : new BigDecimal("40.00");
        FoodOrder o = new FoodOrder(customer, subtotal.setScale(2), tax, fee, ADDRESSES.get(random.nextInt(ADDRESSES.size())),
                "98" + String.format("%08d", 10000000 + random.nextInt(89999999)), random.nextInt(4) == 0 ? NOTES.get(random.nextInt(NOTES.size())) : null);
        lines.forEach(l -> o.addItem((MenuItem) l[0], (Integer) l[1]));
        o.forceState(status, placedAt, by, reason);
        return o;
    }
}
