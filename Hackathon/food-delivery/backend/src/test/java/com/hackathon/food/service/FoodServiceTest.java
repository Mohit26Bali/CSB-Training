package com.hackathon.food.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.food.dto.MenuBody;
import com.hackathon.food.dto.MenuItemResponse;
import com.hackathon.food.dto.OrderLineBody;
import com.hackathon.food.dto.OrderRequest;
import com.hackathon.food.dto.OrderResponse;
import com.hackathon.food.dto.SalesDashboard;
import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.entity.MenuItem;
import com.hackathon.food.entity.OrderStatus;
import com.hackathon.food.entity.Role;
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

@SpringBootTest
@ActiveProfiles("test")
class FoodServiceTest {

    static final String ADDRESS = "Flat 302, Green Park Apartments, Baner, Pune";
    static final String PHONE = "9876543210";

    @Autowired FoodService food;
    @Autowired MenuItemRepository menu;
    @Autowired FoodOrderRepository orders;
    @Autowired UserRepository users;

    MenuItem tikka;
    MenuItem biryani;
    MenuItem naan;
    MenuItem soldOut;
    MenuItem chai;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        menu.deleteAll();
        users.deleteAll();
        users.save(new User("admin", "x", "Chef", Role.RESTAURANT_ADMIN));
        users.save(new User("admin2", "x", "Sous Chef", Role.RESTAURANT_ADMIN));
        for (int i = 1; i <= 5; i++) {
            users.save(new User("c" + i, "x", "Customer " + i, Role.CUSTOMER));
        }
        tikka = dish("Paneer Tikka", MenuCategory.STARTERS, "280.00", true, true);
        biryani = dish("Chicken Biryani", MenuCategory.MAINS, "340.00", false, true);
        naan = dish("Butter Naan", MenuCategory.BREADS_AND_RICE, "60.00", true, true);
        soldOut = dish("Mutton Rogan Josh", MenuCategory.MAINS, "420.00", false, false);
        chai = dish("Masala Chai", MenuCategory.DRINKS, "50.00", true, true);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    MenuItem dish(String name, MenuCategory category, String price, boolean veg, boolean active) {
        return menu.save(new MenuItem(name, "Tasty " + name, category, new BigDecimal(price), veg, active));
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    OrderRequest request(Object... idAndQuantity) {
        List<OrderLineBody> lines = IntStream.range(0, idAndQuantity.length / 2)
                .mapToObj(i -> new OrderLineBody(((MenuItem) idAndQuantity[2 * i]).getId(), (Integer) idAndQuantity[2 * i + 1])).toList();
        return new OrderRequest(lines, ADDRESS, PHONE, null);
    }

    OrderResponse place(String customer, Object... idAndQuantity) {
        loginAs(customer, Role.CUSTOMER);
        return food.place(customer, request(idAndQuantity));
    }

    OrderResponse move(OrderResponse o, OrderStatus to) {
        loginAs("admin", Role.RESTAURANT_ADMIN);
        return food.changeStatus("admin", o.id(), to);
    }

    // ---------- the menu ----------

    @Test
    void customersSeeOnlyActiveDishesAndCanFilter() {
        loginAs("c1", Role.CUSTOMER);
        assertEquals(4, food.menu(null, null, null).size());
        assertEquals(List.of("Chicken Biryani"), food.menu(MenuCategory.MAINS, null, null).stream().map(MenuItemResponse::name).toList());
        assertEquals(List.of("Butter Naan", "Masala Chai", "Paneer Tikka"), food.menu(null, true, null).stream().map(MenuItemResponse::name).sorted().toList());
        assertEquals(List.of("Paneer Tikka"), food.menu(null, null, "tikka").stream().map(MenuItemResponse::name).toList());
        assertEquals(1, food.menu(null, null, "tasty masala").size());
        assertEquals("paneer-tikka", food.menu(null, null, "tikka").get(0).imageKey());
        assertEquals(List.of("STARTERS", "MAINS", "BREADS_AND_RICE", "DESSERTS", "DRINKS"), food.categories());
    }

    @Test
    void theKitchenSeesEveryDishAndManagesTheMenu() {
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertEquals(5, food.adminMenu(null, null, null).size());
        MenuItemResponse made = food.createItem(new MenuBody(" Masala Dosa ", "Crisp crepe", MenuCategory.MAINS, new BigDecimal("150"), true, null));
        assertEquals("Masala Dosa", made.name());
        assertTrue(made.active());
        assertEquals(new BigDecimal("150.00"), made.price());
        FieldValidationException dup = assertThrows(FieldValidationException.class,
                () -> food.createItem(new MenuBody("masala DOSA", null, MenuCategory.MAINS, new BigDecimal("150"), true, true)));
        assertEquals("name", dup.getField());
        MenuItemResponse off = food.updateItem(made.id(), new MenuBody("Masala Dosa", "Crisp crepe", MenuCategory.MAINS, new BigDecimal("160"), true, false));
        assertFalse(off.active());
        assertThrows(FieldValidationException.class, () -> food.updateItem(made.id(), new MenuBody("Butter Naan", null, MenuCategory.MAINS, new BigDecimal("160"), true, true)));
        assertThrows(ResourceNotFoundException.class, () -> food.updateItem(999999L, new MenuBody("X Dish", null, MenuCategory.MAINS, new BigDecimal("160"), true, true)));
        loginAs("c1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> food.adminMenu(null, null, null));
        assertThrows(AccessDeniedException.class, () -> food.createItem(new MenuBody("Hack", null, MenuCategory.MAINS, new BigDecimal("100"), true, true)));
    }

    // ---------- placing orders ----------

    @Test
    void totalsAreCalculatedFromTheMenuWithTaxAndDeliveryFee() {
        OrderResponse big = place("c1", tikka, 2);
        assertEquals(new BigDecimal("560.00"), big.subtotal());
        assertEquals(new BigDecimal("28.00"), big.tax());
        assertEquals(new BigDecimal("0.00"), big.deliveryFee());
        assertEquals(new BigDecimal("588.00"), big.total());
        OrderResponse small = place("c2", tikka, 1);
        assertEquals(new BigDecimal("40.00"), small.deliveryFee());
        assertEquals(new BigDecimal("334.00"), small.total());
        assertEquals("PLACED", small.status());
        assertEquals("ACCEPTED", small.nextStatus());
        assertTrue(small.canCancel());
        assertTrue(small.number().startsWith("FD-"));
    }

    @Test
    void anOrderKeepsTheNamesAndPricesFromWhenItWasPlaced() {
        OrderResponse o = place("c1", biryani, 1, naan, 2);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        food.updateItem(biryani.getId(), new MenuBody("Chicken Biryani Special", null, MenuCategory.MAINS, new BigDecimal("999"), false, true));
        loginAs("c1", Role.CUSTOMER);
        OrderResponse again = food.myOrder("c1", o.id());
        assertEquals(new BigDecimal("460.00"), again.subtotal());
        assertEquals(List.of("Chicken Biryani", "Butter Naan"), again.lines().stream().map(l -> l.name()).toList());
        assertEquals(3, again.itemCount());
    }

    @Test
    void invalidOrdersAreRefusedWithAReason() {
        loginAs("c1", Role.CUSTOMER);
        FieldValidationException twice = assertThrows(FieldValidationException.class, () -> food.place("c1", request(tikka, 1, tikka, 2)));
        assertEquals("items", twice.getField());
        FieldValidationException gone = assertThrows(FieldValidationException.class, () -> food.place("c1", request(tikka, 1, soldOut, 1)));
        assertTrue(gone.getMessage().contains("Mutton Rogan Josh"));
        FieldValidationException unknown = assertThrows(FieldValidationException.class,
                () -> food.place("c1", new OrderRequest(List.of(new OrderLineBody(999999L, 1)), ADDRESS, PHONE, null)));
        assertTrue(unknown.getMessage().contains("does not exist"));
        BusinessRuleException small = assertThrows(BusinessRuleException.class, () -> food.place("c1", request(chai, 1)));
        assertTrue(small.getMessage().contains("minimum order"));
        assertEquals(0, orders.count());
        assertEquals("PLACED", food.place("c1", request(chai, 2)).status());
    }

    @Test
    void theSameOrderTwiceInARowIsTreatedAsADoubleClick() {
        OrderResponse first = place("c1", tikka, 1, naan, 2);
        ConflictException e = assertThrows(ConflictException.class, () -> place("c1", naan, 2, tikka, 1));
        assertTrue(e.getMessage().contains(first.number()));
        assertEquals("PLACED", place("c1", tikka, 2, naan, 2).status());
        assertEquals("PLACED", place("c2", tikka, 1, naan, 2).status());
        assertEquals(3, orders.count());
    }

    @Test
    void onlyCustomersOrderAndOnlyTheKitchenChangesStatus() {
        OrderResponse o = place("c1", tikka, 2);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertThrows(AccessDeniedException.class, () -> food.place("admin", request(tikka, 2)));
        assertThrows(AccessDeniedException.class, () -> food.myOrders("admin", null));
        loginAs("c1", Role.CUSTOMER);
        assertThrows(AccessDeniedException.class, () -> food.changeStatus("c1", o.id(), OrderStatus.ACCEPTED));
        assertThrows(AccessDeniedException.class, () -> food.adminOrders(null, null));
        assertThrows(AccessDeniedException.class, () -> food.dashboard());
    }

    // ---------- following and cancelling ----------

    @Test
    void myOrdersAreSplitIntoActiveAndPastAndPrivate() {
        OrderResponse open = place("c1", tikka, 2);
        OrderResponse done = place("c1", biryani, 2);
        OrderResponse cancelled = place("c1", naan, 4);
        move(move(move(move(done, OrderStatus.ACCEPTED), OrderStatus.PREPARING), OrderStatus.READY), OrderStatus.DELIVERED);
        loginAs("c1", Role.CUSTOMER);
        food.cancelOwn("c1", cancelled.id(), null);
        assertEquals(List.of(open.id()), food.myOrders("c1", "active").stream().map(OrderResponse::id).toList());
        assertEquals(List.of(cancelled.id(), done.id()), food.myOrders("c1", "past").stream().map(OrderResponse::id).toList());
        assertEquals(3, food.myOrders("c1", null).size());
        assertEquals(0, food.myOrders("c2", "all").size());
        assertThrows(FieldValidationException.class, () -> food.myOrders("c1", "later"));
        loginAs("c2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> food.myOrder("c2", open.id()));
        assertThrows(ResourceNotFoundException.class, () -> food.myOrder("c2", 999999L));
    }

    @Test
    void aCustomerCancelsOnlyBeforeTheKitchenAccepts() {
        OrderResponse a = place("c1", tikka, 2);
        OrderResponse b = place("c1", biryani, 2);
        loginAs("c2", Role.CUSTOMER);
        assertThrows(ForbiddenActionException.class, () -> food.cancelOwn("c2", a.id(), null));
        loginAs("c1", Role.CUSTOMER);
        OrderResponse cancelled = food.cancelOwn("c1", a.id(), "Changed my mind");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("c1", cancelled.cancelledBy());
        assertEquals("Changed my mind", cancelled.cancelReason());
        assertFalse(cancelled.canCancel());
        assertNull(cancelled.nextStatus());
        assertThrows(InvalidStatusException.class, () -> food.cancelOwn("c1", a.id(), null));
        move(b, OrderStatus.ACCEPTED);
        loginAs("c1", Role.CUSTOMER);
        InvalidStatusException late = assertThrows(InvalidStatusException.class, () -> food.cancelOwn("c1", b.id(), null));
        assertTrue(late.getMessage().contains("already accepted"));
    }

    // ---------- the kitchen ----------

    @Test
    void ordersMoveOneStepAtATimeAndKeepTheirTimes() {
        OrderResponse o = place("c1", tikka, 2);
        OrderResponse accepted = move(o, OrderStatus.ACCEPTED);
        assertEquals("admin", accepted.acceptedBy());
        assertNotNull(accepted.acceptedAt());
        assertEquals("PREPARING", accepted.nextStatus());
        assertFalse(accepted.canCancel());
        assertNotNull(move(o, OrderStatus.PREPARING).preparingAt());
        assertNotNull(move(o, OrderStatus.READY).readyAt());
        OrderResponse delivered = move(o, OrderStatus.DELIVERED);
        assertNotNull(delivered.deliveredAt());
        assertNull(delivered.nextStatus());
        assertThrows(InvalidStatusException.class, () -> move(o, OrderStatus.DELIVERED));
    }

    @Test
    void skippingOrGoingBackIsRefused() {
        OrderResponse o = place("c1", tikka, 2);
        InvalidStatusException skip = assertThrows(InvalidStatusException.class, () -> move(o, OrderStatus.READY));
        assertTrue(skip.getMessage().contains("ACCEPTED"));
        move(o, OrderStatus.ACCEPTED);
        assertThrows(InvalidStatusException.class, () -> move(o, OrderStatus.PLACED));
        assertThrows(InvalidStatusException.class, () -> move(o, OrderStatus.CANCELLED));
        assertThrows(ResourceNotFoundException.class, () -> food.changeStatus("admin", 999999L, OrderStatus.ACCEPTED));
    }

    @Test
    void theKitchenCancelsWithAReasonUntilTheOrderIsReady() {
        OrderResponse a = place("c1", tikka, 2);
        OrderResponse b = place("c2", biryani, 2);
        OrderResponse c = place("c3", naan, 4);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        FieldValidationException e = assertThrows(FieldValidationException.class, () -> food.cancelAsAdmin("admin", a.id(), "  "));
        assertEquals("reason", e.getField());
        OrderResponse cancelled = food.cancelAsAdmin("admin", a.id(), "Out of paneer");
        assertEquals("CANCELLED", cancelled.status());
        assertEquals("Out of paneer", cancelled.cancelReason());
        assertThrows(InvalidStatusException.class, () -> food.cancelAsAdmin("admin", a.id(), "again"));
        move(move(b, OrderStatus.ACCEPTED), OrderStatus.PREPARING);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertEquals("CANCELLED", food.cancelAsAdmin("admin", b.id(), "Oven broke").status());
        move(move(move(c, OrderStatus.ACCEPTED), OrderStatus.PREPARING), OrderStatus.READY);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertThrows(InvalidStatusException.class, () -> food.cancelAsAdmin("admin", c.id(), "Too late"));
    }

    @Test
    void theOrderBoardPutsOpenOrdersFirstAndSearches() {
        OrderResponse first = place("c1", tikka, 2);
        OrderResponse second = place("c2", biryani, 2);
        OrderResponse third = place("c3", naan, 4);
        move(move(move(move(first, OrderStatus.ACCEPTED), OrderStatus.PREPARING), OrderStatus.READY), OrderStatus.DELIVERED);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertEquals(List.of(third.id(), second.id(), first.id()), food.adminOrders(null, null).stream().map(OrderResponse::id).toList());
        assertEquals(List.of(first.id()), food.adminOrders(OrderStatus.DELIVERED, null).stream().map(OrderResponse::id).toList());
        assertEquals(List.of(second.id()), food.adminOrders(null, "customer 2").stream().map(OrderResponse::id).toList());
        assertEquals(List.of(third.id()), food.adminOrders(null, third.number().toLowerCase()).stream().map(OrderResponse::id).toList());
        assertEquals(PHONE, food.adminOrders(null, PHONE).get(0).phone());
    }

    @Test
    void theDashboardSummarisesSales() {
        OrderResponse delivered = place("c1", tikka, 2);
        OrderResponse cancelled = place("c2", biryani, 2);
        place("c3", tikka, 1);
        move(move(move(move(delivered, OrderStatus.ACCEPTED), OrderStatus.PREPARING), OrderStatus.READY), OrderStatus.DELIVERED);
        loginAs("c2", Role.CUSTOMER);
        food.cancelOwn("c2", cancelled.id(), null);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        SalesDashboard d = food.dashboard();
        assertEquals(2, d.ordersToday());
        assertEquals(new BigDecimal("922.00"), d.revenueToday());
        assertEquals(1, d.openOrders());
        assertEquals(new BigDecimal("461.00"), d.averageOrder());
        assertEquals(1, d.deliveredThisWeek());
        assertEquals(new BigDecimal("922.00"), d.revenueThisWeek());
        assertEquals("Paneer Tikka", d.bestSellers().get(0).name());
        assertEquals(3, d.bestSellers().get(0).quantity());
        assertEquals(1, d.bestSellers().size());
        assertEquals(6, d.byStatus().size());
        assertEquals(1, d.byStatus().stream().filter(s -> s.status().equals("CANCELLED")).findFirst().orElseThrow().count());
        assertEquals(7, d.lastSevenDays().size());
        assertEquals(2, d.lastSevenDays().get(6).orders());
    }

    // ---------- concurrency ----------

    private static long winners(List<Future<Boolean>> results) throws Exception {
        long wins = 0;
        for (Future<Boolean> f : results) {
            if (f.get()) {
                wins++;
            }
        }
        return wins;
    }

    @Test
    void aDoubleClickedCheckoutMakesOneOrder() throws Exception {
        OrderRequest same = request(tikka, 2, naan, 2);
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<Boolean>> tasks = IntStream.range(0, 5).<Callable<Boolean>>mapToObj(i -> () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    food.place("c1", same);
                    return true;
                } catch (ConflictException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(1, orders.count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void twoStaffAcceptingTheSameOrderAcceptItOnce() throws Exception {
        OrderResponse o = place("c1", tikka, 2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Boolean>> tasks = List.of("admin", "admin2").stream().<Callable<Boolean>>map(who -> () -> {
                loginAs(who, Role.RESTAURANT_ADMIN);
                try {
                    food.changeStatus(who, o.id(), OrderStatus.ACCEPTED);
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            assertEquals(1, winners(pool.invokeAll(tasks)));
            assertEquals(OrderStatus.ACCEPTED, orders.findById(o.id()).orElseThrow().getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aCustomerCancellingWhileTheKitchenAcceptsEndsConsistently() throws Exception {
        OrderResponse o = place("c1", tikka, 2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Boolean> cancel = () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    food.cancelOwn("c1", o.id(), "Changed my mind");
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            Callable<Boolean> accept = () -> {
                loginAs("admin", Role.RESTAURANT_ADMIN);
                try {
                    food.changeStatus("admin", o.id(), OrderStatus.ACCEPTED);
                    return true;
                } catch (InvalidStatusException ex) {
                    return false;
                } finally {
                    SecurityContextHolder.clearContext();
                }
            };
            assertEquals(1, winners(pool.invokeAll(List.of(cancel, accept))));
            OrderStatus status = orders.findById(o.id()).orElseThrow().getStatus();
            assertTrue(status == OrderStatus.CANCELLED || status == OrderStatus.ACCEPTED);
        } finally {
            pool.shutdownNow();
        }
    }
}
