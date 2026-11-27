package com.hackathon.food.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
import com.hackathon.food.entity.OrderEvent;
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
import com.hackathon.food.repo.OrderEventRepository;
import com.hackathon.food.repo.UserRepository;

/**
 * The menu, orders and the kitchen's work. Prices always come from the menu, never from the client. Placing an order locks the customer
 * row, so a double click makes one order; every status change and cancellation locks the order row first, so two staff members cannot
 * both move the same order and a customer cannot cancel while the kitchen accepts. Every status change is also written to order_events,
 * with who did it and when. Times use the restaurant's clock (app.timezone). The order board and the sales figures are computed in SQL.
 */
@Service
public class FoodService {

    static final BigDecimal TAX_RATE = new BigDecimal("0.05");
    static final BigDecimal MIN_ORDER = new BigDecimal("100.00");
    static final BigDecimal FREE_DELIVERY_FROM = new BigDecimal("500.00");
    static final BigDecimal DELIVERY_FEE = new BigDecimal("40.00");
    static final long DUPLICATE_WINDOW_SECONDS = 30;
    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private static final Set<OrderStatus> OPEN = EnumSet.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY);
    private static final Set<OrderStatus> PAST = EnumSet.of(OrderStatus.DELIVERED, OrderStatus.CANCELLED);
    private static final Set<OrderStatus> ALL = EnumSet.allOf(OrderStatus.class);

    /** The result of placing an order: the order, and whether this call created it (false when a retry returned the earlier one). */
    public record Placement(OrderResponse order, boolean created) {
    }

    private final MenuItemRepository menu;
    private final FoodOrderRepository orders;
    private final OrderEventRepository events;
    private final UserRepository users;
    private final Clock clock;

    public FoodService(MenuItemRepository menu, FoodOrderRepository orders, OrderEventRepository events, UserRepository users, Clock clock) {
        this.menu = menu;
        this.orders = orders;
        this.events = events;
        this.users = users;
        this.clock = clock;
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
    @Transactional(isolation = Isolation.READ_COMMITTED)   // needed here too: a call from the overload below does not pass through the proxy
    public OrderResponse place(String username, OrderRequest request) {
        return place(username, request, null).order();
    }

    /**
     * Places an order. With an idempotency key (a client-generated id for this checkout) a retry returns the order the first attempt made
     * instead of making a second one; without a key the same dishes to the same address within 30 seconds are refused as a double click.
     */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Placement place(String username, OrderRequest request, String idempotencyKey) {
        String key = cleanKey(idempotencyKey);
        Set<Long> seen = new HashSet<>();
        for (OrderLineBody line : request.items()) {
            if (!seen.add(line.menuItemId())) {
                throw new FieldValidationException("items", "The same dish is in the order twice. Change its quantity instead");
            }
        }
        User customer = users.findByUsernameForUpdate(username).orElseThrow(() -> new ResourceNotFoundException("User " + username + " not found"));
        if (key != null) {
            Optional<FoodOrder> earlier = orders.findByCustomerAndIdempotencyKey(customer, key);
            if (earlier.isPresent()) {
                if (!signature(earlier.get()).equals(signature(request))) {
                    throw new ConflictException("This checkout was already used for a different order. Start the checkout again");
                }
                return new Placement(view(earlier.get()), false);
            }
        }
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
        if (key == null) {
            rejectDoubleClick(customer, request);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        BigDecimal tax = subtotal.multiply(TAX_RATE).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = subtotal.compareTo(FREE_DELIVERY_FROM) >= 0 ? BigDecimal.ZERO.setScale(2) : DELIVERY_FEE;
        FoodOrder order = new FoodOrder(customer, subtotal.setScale(2, RoundingMode.HALF_UP), tax, fee, request.address().trim(), request.phone(), clean(request.notes()), now);
        order.setIdempotencyKey(key);
        request.items().forEach(line -> order.addItem(dishes.get(line.menuItemId()), line.quantity()));
        FoodOrder saved = orders.save(order);
        events.save(new OrderEvent(saved, OrderStatus.PLACED, username, now));
        return new Placement(view(saved), true);
    }

    private static String cleanKey(String key) {
        String trimmed = clean(key);
        if (trimmed != null && !IDEMPOTENCY_KEY.matcher(trimmed).matches()) {
            throw new FieldValidationException("idempotencyKey", "The idempotency key must be 8 to 64 letters, digits, '-' or '_'");
        }
        return trimmed;
    }

    /** The same dishes to the same address within seconds of the last order is almost certainly a double click. */
    private void rejectDoubleClick(User customer, OrderRequest request) {
        orders.findTopByCustomerOrderByIdDesc(customer).ifPresent(last -> {
            boolean recent = last.getCreatedAt().isAfter(LocalDateTime.now(clock).minusSeconds(DUPLICATE_WINDOW_SECONDS));
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
        return myOrders(username, scope, null, null);
    }

    /** Same, one page at a time (page from 0, size 1 to 100). Without page and size, every matching order. */
    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public List<OrderResponse> myOrders(String username, String scope, Integer page, Integer size) {
        String wanted = scope == null || scope.isBlank() ? "all" : scope.toLowerCase(Locale.ROOT);
        if (!Set.of("all", "active", "past").contains(wanted)) {
            throw new FieldValidationException("scope", "Scope must be active, past or all");
        }
        Set<OrderStatus> statuses = switch (wanted) {
            case "active" -> OPEN;
            case "past" -> PAST;
            default -> ALL;
        };
        return load(orders.customerOrderIds(user(username), statuses, pageable(page, size)));
    }

    @PreAuthorize("hasRole('CUSTOMER')")
    @Transactional(readOnly = true)
    public OrderResponse myOrder(String username, Long id) {
        FoodOrder order = detailed(id);
        if (!order.getCustomer().getUsername().equals(username)) {
            throw new ForbiddenActionException("You can only see your own orders");
        }
        return view(order);
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
        LocalDateTime now = LocalDateTime.now(clock);
        order.cancel(username, clean(reason), now);
        events.save(new OrderEvent(order, OrderStatus.CANCELLED, username, now));
        return view(order);
    }

    // ---------- the kitchen ----------

    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(readOnly = true)
    public List<OrderResponse> adminOrders(OrderStatus status, String search) {
        return adminOrders(status, false, search, null, null);
    }

    /**
     * The order board, filtered and paged in SQL. status picks one status; openOnly (used by the live board) keeps only orders that are
     * not delivered or cancelled. Open orders come first, newest first. Without page and size, every matching order.
     */
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(readOnly = true)
    public List<OrderResponse> adminOrders(OrderStatus status, boolean openOnly, String search, Integer page, Integer size) {
        Set<OrderStatus> statuses = status != null ? EnumSet.of(status) : openOnly ? OPEN : ALL;
        return load(orders.boardIds(statuses, OPEN, likePattern(search), pageable(page, size)));
    }

    /** "%text%" in lower case; the characters that mean something to LIKE are treated as spaces. */
    private static String likePattern(String search) {
        String q = search == null ? "" : search.toLowerCase(Locale.ROOT).replace('%', ' ').replace('_', ' ').trim();
        return "%" + q + "%";
    }

    private static Pageable pageable(Integer page, Integer size) {
        if (page == null && size == null) {
            return Pageable.unpaged();
        }
        int p = page == null ? 0 : page;
        int s = size == null ? DEFAULT_PAGE_SIZE : size;
        if (p < 0) {
            throw new FieldValidationException("page", "Page must be 0 or more");
        }
        if (s < 1 || s > MAX_PAGE_SIZE) {
            throw new FieldValidationException("size", "Size must be between 1 and " + MAX_PAGE_SIZE);
        }
        return PageRequest.of(p, s);
    }

    /** Loads the orders for these ids (customer and lines in one query) and keeps the order of the ids. */
    private List<OrderResponse> load(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Long, FoodOrder> byId = orders.findByIdIn(ids).stream().collect(Collectors.toMap(FoodOrder::getId, Function.identity()));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(this::view).toList();
    }

    private OrderResponse view(FoodOrder order) {
        return OrderResponse.of(order, clock.getZone());
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
        LocalDateTime now = LocalDateTime.now(clock);
        order.moveTo(next, admin, now);
        events.save(new OrderEvent(order, next, admin, now));
        return view(order);
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
        LocalDateTime now = LocalDateTime.now(clock);
        order.cancel(admin, why, now);
        events.save(new OrderEvent(order, OrderStatus.CANCELLED, admin, now));
        return view(order);
    }

    /**
     * The sales page. Totals, best sellers and status counts are SQL aggregates; only the last seven days of orders are read row by row
     * (for today and the daily chart). "Today" follows the restaurant's time zone.
     */
    @PreAuthorize("hasRole('RESTAURANT_ADMIN')")
    @Transactional(readOnly = true)
    public SalesDashboard dashboard() {
        LocalDate today = LocalDate.now(clock);
        LocalDate weekStart = today.minusDays(6);
        LocalDateTime weekFrom = weekStart.atStartOfDay();
        List<FoodOrder> week = orders.findByStatusNotAndCreatedAtGreaterThanEqual(OrderStatus.CANCELLED, weekFrom);
        List<FoodOrder> todays = week.stream().filter(o -> o.getCreatedAt().toLocalDate().equals(today)).toList();
        long soldCount = orders.countExcluding(OrderStatus.CANCELLED);
        BigDecimal average = soldCount == 0 ? BigDecimal.ZERO.setScale(2)
                : orders.sumTotalExcluding(OrderStatus.CANCELLED).divide(BigDecimal.valueOf(soldCount), 2, RoundingMode.HALF_UP);
        Map<OrderStatus, Long> byStatus = orders.countByStatus().stream()
                .collect(Collectors.toMap(FoodOrderRepository.StatusTotal::getStatus, FoodOrderRepository.StatusTotal::getTotal));
        long open = OPEN.stream().mapToLong(st -> byStatus.getOrDefault(st, 0L)).sum();
        return new SalesDashboard(todays.size(), sum(todays), open, average,
                orders.countByStatusAndCreatedAtGreaterThanEqual(OrderStatus.DELIVERED, weekFrom), sum(week),
                EnumSet.allOf(OrderStatus.class).stream().map(st -> new StatusCount(st.name(), byStatus.getOrDefault(st, 0L))).toList(),
                orders.bestSellers(OrderStatus.CANCELLED, PageRequest.of(0, 5)).stream().map(e -> new ItemSales(e.getName(), e.getQuantity())).toList(),
                IntStream.rangeClosed(0, 6).mapToObj(i -> {
                    LocalDate d = weekStart.plusDays(i);
                    List<FoodOrder> ofDay = week.stream().filter(o -> o.getCreatedAt().toLocalDate().equals(d)).toList();
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
