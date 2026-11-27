package com.hackathon.food.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.hackathon.food.dto.DayRevenue;
import com.hackathon.food.dto.ItemSales;
import com.hackathon.food.dto.MenuBody;
import com.hackathon.food.dto.MenuItemResponse;
import com.hackathon.food.dto.OrderLineBody;
import com.hackathon.food.dto.OrderRequest;
import com.hackathon.food.dto.OrderResponse;
import com.hackathon.food.dto.SalesDashboard;
import com.hackathon.food.dto.StatusCount;
import com.hackathon.food.entity.FoodOrder;
import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.entity.MenuItem;
import com.hackathon.food.entity.OrderItem;
import com.hackathon.food.entity.OrderStatus;
import com.hackathon.food.entity.User;
import com.hackathon.food.exception.BusinessRuleException;
import com.hackathon.food.exception.ConflictException;
import com.hackathon.food.exception.FieldValidationException;
import com.hackathon.food.exception.ForbiddenActionException;
import com.hackathon.food.exception.InvalidStatusException;
import com.hackathon.food.exception.ResourceNotFoundException;
import com.hackathon.food.repo.FoodOrderRepository;
import com.hackathon.food.repo.MenuItemRepository;
import com.hackathon.food.repo.UserRepository;

/**
 * The menu, orders and the kitchen's work. Prices always come from the menu, never from the client. Placing an order locks the customer
 * row, so a double click makes one order; every status change and cancellation locks the order row first, so two staff members cannot
 * both move the same order and a customer cannot cancel while the kitchen accepts.
 */
@Service
public class FoodService {

    static final BigDecimal TAX_RATE = new BigDecimal("0.05");
    static final BigDecimal MIN_ORDER = new BigDecimal("100.00");
    static final BigDecimal FREE_DELIVERY_FROM = new BigDecimal("500.00");
    static final BigDecimal DELIVERY_FEE = new BigDecimal("40.00");
    static final long DUPLICATE_WINDOW_SECONDS = 30;

    private final MenuItemRepository menu;
    private final FoodOrderRepository orders;
    private final UserRepository users;

    public FoodService(MenuItemRepository menu, FoodOrderRepository orders, UserRepository users) {
        this.menu = menu;
        this.orders = orders;
        this.users = users;
    }

    // ---------- the menu ----------

    /** Active dishes, optionally filtered by category, vegetarian only and a word in the name or description. */
    @Transactional(readOnly = true)
    public List<MenuItemResponse> menu(MenuCategory category, Boolean vegetarian, String search) {
        return filter(menu.findAllByOrderByCategoryAscNameAsc().stream().filter(MenuItem::isActive), category, vegetarian, search);
    }

    /** Every dish including the switched-off ones. */
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(readOnly = true)
    public List<MenuItemResponse> adminMenu(MenuCategory category, Boolean vegetarian, String search) {
        return filter(menu.findAllByOrderByCategoryAscNameAsc().stream(), category, vegetarian, search);
    }

    private static List<MenuItemResponse> filter(Stream<MenuItem> dishes, MenuCategory category, Boolean vegetarian, String search) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return dishes.filter(m -> category == null || m.getCategory() == category)
                .filter(m -> vegetarian == null || !vegetarian || m.isVegetarian())
                .filter(m -> q.isEmpty() || m.getName().toLowerCase(Locale.ROOT).contains(q)
                        || (m.getDescription() != null && m.getDescription().toLowerCase(Locale.ROOT).contains(q)))
                .map(MenuItemResponse::of).toList();
    }

    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional
    public MenuItemResponse createItem(MenuBody body) {
        String name = body.name().trim();
        requireFreeName(null, name);
        try {
            return MenuItemResponse.of(menu.saveAndFlush(new MenuItem(name, clean(body.description()), body.category(), body.price().setScale(2, RoundingMode.HALF_UP),
                    Boolean.TRUE.equals(body.vegetarian()), body.active() == null || body.active())));
        } catch (DataIntegrityViolationException e) {
            throw new FieldValidationException("name", "There is already a dish called " + name);
        }
    }

    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional
    public MenuItemResponse updateItem(Long id, MenuBody body) {
        MenuItem item = menu.findById(id).orElseThrow(() -> new ResourceNotFoundException("Dish " + id + " not found"));
        String name = body.name().trim();
        requireFreeName(id, name);
        item.update(name, clean(body.description()), body.category(), body.price().setScale(2, RoundingMode.HALF_UP),
                Boolean.TRUE.equals(body.vegetarian()), body.active() == null || body.active());
        return MenuItemResponse.of(item);
    }

    private void requireFreeName(Long id, String name) {
        if (menu.findByNameIgnoreCase(name).filter(m -> !m.getId().equals(id)).isPresent()) {
            throw new FieldValidationException("name", "There is already a dish called " + name);
        }
    }

    // ---------- customers: ordering ----------

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse place(String username, OrderRequest request) {
        Set<Long> seen = new HashSet<>();
        for (OrderLineBody line : request.items()) {
            if (!seen.add(line.menuItemId())) {
                throw new FieldValidationException("items", "The same dish is in the order twice. Change its quantity instead");
            }
        }
        User customer = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
        Map<Long, MenuItem> dishes = menu.findAllById(seen).stream().collect(Collectors.toMap(MenuItem::getId, m -> m));
        BigDecimal subtotal = BigDecimal.ZERO;
        for (OrderLineBody line : request.items()) {
            MenuItem dish = dishes.get(line.menuItemId());
            if (dish == null) {
                throw new FieldValidationException("items", "Dish " + line.menuItemId() + " does not exist");
            }
            if (!dish.isActive()) {
                throw new FieldValidationException("items", dish.getName() + " is not available right now. Remove it to continue");
            }
            subtotal = subtotal.add(dish.getPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        if (subtotal.compareTo(MIN_ORDER) < 0) {
            throw new BusinessRuleException("The minimum order is ₹" + MIN_ORDER.toBigInteger() + " (yours is ₹" + subtotal.toBigInteger() + ")");
        }
        rejectDoubleClick(customer, request);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = subtotal.compareTo(FREE_DELIVERY_FROM) >= 0 ? BigDecimal.ZERO.setScale(2) : DELIVERY_FEE;
        FoodOrder order = new FoodOrder(customer, subtotal.setScale(2, RoundingMode.HALF_UP), tax, fee, request.address().trim(), request.phone(), clean(request.notes()));
        request.items().forEach(line -> order.addItem(dishes.get(line.menuItemId()), line.quantity()));
        return OrderResponse.of(orders.save(order));
    }

    /** The same dishes to the same address within seconds of the last order is almost certainly a double click. */
    private void rejectDoubleClick(User customer, OrderRequest request) {
        orders.findTopByCustomerOrderByIdDesc(customer).ifPresent(last -> {
            boolean recent = last.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(DUPLICATE_WINDOW_SECONDS));
            if (recent && signature(last).equals(signature(request))) {
                throw new ConflictException("You just placed this same order (FD-" + (1000 + last.getId()) + "). Check My orders before ordering again");
            }
        });
    }

    private static String signature(FoodOrder o) {
        return o.getItems().stream().map(i -> i.getMenuItem().getId() + "x" + i.getQuantity()).sorted().collect(Collectors.joining(",")) + "@" + o.getAddress();
    }

    private static String signature(OrderRequest r) {
        return r.items().stream().map(i -> i.menuItemId() + "x" + i.quantity()).sorted().collect(Collectors.joining(",")) + "@" + r.address().trim();
    }

    /** Own orders. scope: active (placed to ready), past (delivered or cancelled) or all; newest first. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(String username, String scope) {
        String wanted = scope == null || scope.isBlank() ? "all" : scope.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "active", "past").contains(wanted)) {
            throw new FieldValidationException("scope", "Scope must be active, past or all");
        }
        return orders.findByCustomerOrderByIdDesc(user(username)).stream()
                .filter(o -> wanted.equals("all") || o.getStatus().isOpen() == wanted.equals("active"))
                .map(OrderResponse::of).toList();
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public OrderResponse myOrder(String username, Long id) {
        FoodOrder order = detailed(id);
        if (!order.getCustomer().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only see your own orders");
        }
        return OrderResponse.of(order);
    }

    /** A customer can cancel only while the kitchen has not accepted the order yet. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse cancelOwn(String username, Long id, String reason) {
        FoodOrderRepository.Peek peek = orders.peek(id).orElseThrow(() -> notFound(id));
        if (!peek.getCustomerUsername().equals(username)) {
            throw new ForbiddenActionException("You can only cancel your own orders");
        }
        FoodOrder order = orders.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (order.getStatus() != OrderStatus.PLACED) {
            throw new InvalidStatusException(order.getStatus() == OrderStatus.CANCELLED
                    ? "This order is already cancelled"
                    : "The kitchen has already accepted this order (" + order.getStatus() + "), so it can no longer be cancelled");
        }
        order.cancel(username, clean(reason));
        return OrderResponse.of(order);
    }

    // ---------- the kitchen ----------

    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(readOnly = true)
    public List<OrderResponse> adminOrders(OrderStatus status, String search) {
        String q = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return orders.findAllByOrderByIdDesc().stream()
                .filter(o -> status == null || o.getStatus() == status)
                .filter(o -> q.isEmpty() || Stream.of("fd-" + (1000 + o.getId()), o.getCustomer().getFullname(), o.getCustomer().getUsername(), o.getPhone())
                        .anyMatch(s -> s.toLowerCase(Locale.ROOT).contains(q)))
                .sorted(Comparator.comparing((FoodOrder o) -> o.getStatus().isOpen() ? 0 : 1).thenComparing(FoodOrder::getId, Comparator.reverseOrder()))
                .map(OrderResponse::of).toList();
    }

    /** Orders move one step at a time: PLACED, ACCEPTED, PREPARING, READY, DELIVERED. */
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse changeStatus(String admin, Long id, OrderStatus target) {
        FoodOrder order = orders.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        OrderStatus next = order.getStatus().next();
        if (next == null) {
            throw new InvalidStatusException("This order is " + order.getStatus() + " and cannot change any more");
        }
        if (target != next) {
            throw new InvalidStatusException("An order that is " + order.getStatus() + " can only move to " + next + ", not " + target);
        }
        order.moveTo(next, admin);
        return OrderResponse.of(order);
    }

    /** The kitchen can reject an order up to the moment it is ready, and must say why. */
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public OrderResponse cancelAsAdmin(String admin, Long id, String reason) {
        String why = clean(reason);
        if (why == null) {
            throw new FieldValidationException("reason", "Tell the customer why the order is being cancelled");
        }
        FoodOrder order = orders.findByIdForUpdate(id).orElseThrow(() -> notFound(id));
        if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.DELIVERED || order.getStatus() == OrderStatus.READY) {
            throw new InvalidStatusException("An order that is " + order.getStatus() + " can no longer be cancelled");
        }
        order.cancel(admin, why);
        return OrderResponse.of(order);
    }

    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(readOnly = true)
    public SalesDashboard dashboard() {
        LocalDate today = LocalDate.now();
        LocalDate weekStart = today.minusDays(6);
        List<FoodOrder> all = orders.findAllByOrderByIdDesc();
        List<FoodOrder> sold = all.stream().filter(o -> o.getStatus() != OrderStatus.CANCELLED).toList();
        List<FoodOrder> todays = sold.stream().filter(o -> o.getCreatedAt().toLocalDate().equals(today)).toList();
        List<FoodOrder> week = sold.stream().filter(o -> !o.getCreatedAt().toLocalDate().isBefore(weekStart)).toList();
        BigDecimal revenueToday = sum(todays);
        BigDecimal average = sold.isEmpty() ? BigDecimal.ZERO.setScale(2) : sum(sold).divide(BigDecimal.valueOf(sold.size()), 2, RoundingMode.HALF_UP);
        Map<String, Long> bestSellers = sold.stream().flatMap(o -> o.getItems().stream())
                .collect(Collectors.groupingBy(OrderItem::getItemName, Collectors.summingLong(OrderItem::getQuantity)));
        Map<OrderStatus, Long> byStatus = all.stream().collect(Collectors.groupingBy(FoodOrder::getStatus, Collectors.counting()));
        return new SalesDashboard(todays.size(), revenueToday, all.stream().filter(o -> o.getStatus().isOpen()).count(), average,
                all.stream().filter(o -> o.getStatus() == OrderStatus.DELIVERED && !o.getCreatedAt().toLocalDate().isBefore(weekStart)).count(), sum(week),
                EnumSet.allOf(OrderStatus.class).stream().map(s -> new StatusCount(s.name(), byStatus.getOrDefault(s, 0L))).toList(),
                bestSellers.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                        .limit(5).map(e -> new ItemSales(e.getKey(), e.getValue())).toList(),
                IntStream.rangeClosed(0, 6).mapToObj(i -> {
                    LocalDate d = weekStart.plusDays(i);
                    List<FoodOrder> ofDay = sold.stream().filter(o -> o.getCreatedAt().toLocalDate().equals(d)).toList();
                    return new DayRevenue(d, ofDay.size(), sum(ofDay));
                }).toList());
    }

    private static BigDecimal sum(List<FoodOrder> list) {
        return list.stream().map(FoodOrder::getTotal).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    // ---------- helpers ----------

    /** Category names in menu order, for the filter bar. */
    public List<String> categories() {
        return Stream.of(MenuCategory.values()).map(Enum::name).toList();
    }

    private User user(String username) {
        return users.findByUsername(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
    }

    private FoodOrder detailed(Long id) {
        return orders.findDetailedById(id).orElseThrow(() -> notFound(id));
    }

    private static ResourceNotFoundException notFound(Long id) {
        return new ResourceNotFoundException("Order " + id + " not found");
    }

    private static String clean(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }
}
