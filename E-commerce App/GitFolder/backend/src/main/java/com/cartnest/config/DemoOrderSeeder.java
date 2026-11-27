package com.cartnest.config;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cartnest.entity.Address;
import com.cartnest.entity.AddressType;
import com.cartnest.entity.CustomerOrder;
import com.cartnest.entity.OrderItem;
import com.cartnest.entity.OrderStatus;
import com.cartnest.entity.PaymentMethod;
import com.cartnest.entity.PaymentStatus;
import com.cartnest.entity.ProductVariant;
import com.cartnest.entity.Role;
import com.cartnest.entity.User;
import com.cartnest.repository.AddressRepository;
import com.cartnest.repository.OrderRepository;
import com.cartnest.repository.ProductVariantRepository;
import com.cartnest.repository.UserRepository;
import com.cartnest.service.OrderService;

@Component
@ConditionalOnProperty(name = "app.seed.demo-orders", havingValue = "true")
public class DemoOrderSeeder implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(DemoOrderSeeder.class);
    private static final int ORDERS = 160;
    private static final int DAYS = 45;
    private static final BigDecimal MAX_ITEM_PRICE = new BigDecimal("60000");
    private static final String[][] CITIES = {
            { "Mumbai", "Maharashtra", "400001" }, { "Pune", "Maharashtra", "411001" },
            { "Bengaluru", "Karnataka", "560001" }, { "Delhi", "Delhi", "110001" },
            { "Hyderabad", "Telangana", "500001" }, { "Chennai", "Tamil Nadu", "600001" },
            { "Kolkata", "West Bengal", "700001" }, { "Ahmedabad", "Gujarat", "380001" },
            { "Jaipur", "Rajasthan", "302001" }, { "Lucknow", "Uttar Pradesh", "226001" },
            { "Kochi", "Kerala", "682001" }, { "Indore", "Madhya Pradesh", "452001" } };

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final ProductVariantRepository variantRepository;

    public DemoOrderSeeder(OrderRepository orderRepository, UserRepository userRepository,
                           AddressRepository addressRepository, ProductVariantRepository variantRepository) {
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.addressRepository = addressRepository;
        this.variantRepository = variantRepository;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (orderRepository.count() >= 20) {
            return;
        }
        Random random = new Random(2026);
        LocalDateTime now = LocalDateTime.now();
        List<User> customers = userRepository.findAll().stream().filter(u -> u.getRole() == Role.CUSTOMER).toList();
        List<ProductVariant> variants = variantRepository.findAll().stream()
                .filter(v -> v.getProduct().isActive() && v.getStock() > 8 && v.getPrice().compareTo(MAX_ITEM_PRICE) <= 0)
                .toList();
        Map<Long, Address> addresses = new HashMap<>();

        for (int n = 0; n < ORDERS; n++) {
            int ageDays = (int) Math.floor(Math.pow(random.nextDouble(), 1.6) * DAYS);
            LocalDateTime placedAt = now.minusDays(ageDays).minusMinutes(random.nextInt(ageDays == 0 ? 600 : 1440));
            User customer = customers.get(random.nextInt(customers.size()));
            Address address = addresses.computeIfAbsent(customer.getId(), id -> addressFor(customer, random));
            PaymentMethod method = PaymentMethod.values()[random.nextInt(10) < 4 ? 0 : random.nextInt(10) < 6 ? 1 : 2];

            String number = "CN" + placedAt.format(DateTimeFormatter.ofPattern("yyMMdd"))
                    + String.format("%06d", random.nextInt(1_000_000));
            CustomerOrder order = new CustomerOrder(number, customer, method, address);
            int lines = 1 + random.nextInt(3);
            List<ProductVariant> picked = new ArrayList<>();
            for (int i = 0; i < lines; i++) {
                ProductVariant v = variants.get(random.nextInt(variants.size()));
                if (!picked.contains(v)) {
                    picked.add(v);
                    order.addItem(v, v.getPrice().compareTo(new BigDecimal("2000")) < 0 ? 1 + random.nextInt(2) : 1);
                }
            }
            BigDecimal totalMrp = order.getItems().stream()
                    .map(i -> i.getUnitMrp().multiply(BigDecimal.valueOf(i.getQuantity())))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal itemTotal = order.getItems().stream().map(OrderItem::getLineTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (method == PaymentMethod.COD && itemTotal.compareTo(OrderService.COD_LIMIT) > 0) {
                method = PaymentMethod.UPI;
                order = rebuild(order, number, customer, method, address);
            }
            order.setTotals(totalMrp, itemTotal, null, BigDecimal.ZERO, OrderService.deliveryFee(itemTotal));
            order.backdate(placedAt);
            if (method != PaymentMethod.COD) {
                order.markPaid("PAY" + Long.toString(Math.abs(random.nextLong()), 36).toUpperCase());
            }
            advance(order, ageDays, placedAt, now, random);
            if (order.getStatus() != OrderStatus.CANCELLED) {
                order.getItems().forEach(i -> i.getVariant().setStock(Math.max(3, i.getVariant().getStock() - i.getQuantity())));
            }
            orderRepository.save(order);
        }
        log.info("Demo data: created {} orders spread over the last {} days", ORDERS, DAYS);
    }

    private static void advance(CustomerOrder order, int ageDays, LocalDateTime placedAt, LocalDateTime now, Random random) {
        String system = "admin@cartnest.in";
        order.moveTo(OrderStatus.PLACED, "Order placed", order.getUser().getEmail(), placedAt);
        if (random.nextInt(9) == 0) {
            order.setCancelReason("Changed my mind");
            order.setPaymentStatus(order.getPaymentStatus() == PaymentStatus.PAID ? PaymentStatus.REFUNDED : PaymentStatus.CANCELLED);
            order.moveTo(OrderStatus.CANCELLED, "Changed my mind", order.getUser().getEmail(), min(placedAt.plusHours(3), now));
            return;
        }
        if (ageDays >= 1) {
            order.moveTo(OrderStatus.CONFIRMED, "Order confirmed by the seller", system, min(placedAt.plusHours(4), now));
        }
        if (ageDays >= 3) {
            order.moveTo(OrderStatus.SHIPPED, "Handed over to the courier", system, min(placedAt.plusDays(1), now));
        }
        if (ageDays >= 6) {
            if (order.getPaymentMethod() == PaymentMethod.COD) {
                order.markPaid("COD" + Long.toString(Math.abs(random.nextLong()), 36).toUpperCase());
            }
            order.moveTo(OrderStatus.DELIVERED, "Delivered", system, min(placedAt.plusDays(3 + random.nextInt(3)), now));
        }
    }

    private static CustomerOrder rebuild(CustomerOrder old, String number, User customer, PaymentMethod method, Address address) {
        CustomerOrder order = new CustomerOrder(number, customer, method, address);
        old.getItems().forEach(i -> order.addItem(i.getVariant(), i.getQuantity()));
        return order;
    }

    private Address addressFor(User customer, Random random) {
        return addressRepository.findByUserIdOrderByDefaultAddressDescCreatedAtDescIdDesc(customer.getId()).stream()
                .findFirst()
                .orElseGet(() -> {
                    String[] city = CITIES[random.nextInt(CITIES.length)];
                    Address a = new Address(customer);
                    a.setFullName(customer.getFullName());
                    a.setPhone("9" + String.format("%09d", random.nextInt(1_000_000_000)));
                    a.setLine1((1 + random.nextInt(200)) + ", " + new String[] { "MG Road", "Park Street", "Station Road",
                            "Lake View Colony", "Gandhi Nagar", "Civil Lines" }[random.nextInt(6)]);
                    a.setCity(city[0]);
                    a.setState(city[1]);
                    a.setPincode(city[2]);
                    a.setType(AddressType.HOME);
                    a.setDefaultAddress(true);
                    return addressRepository.save(a);
                });
    }

    private static LocalDateTime min(LocalDateTime a, LocalDateTime b) {
        return a.isBefore(b) ? a : b;
    }
}
