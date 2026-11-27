package com.hackathon.food.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;

import com.hackathon.food.dto.OrderLineBody;
import com.hackathon.food.dto.OrderRequest;
import com.hackathon.food.dto.OrderResponse;
import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.entity.MenuItem;
import com.hackathon.food.entity.OrderEvent;
import com.hackathon.food.entity.OrderStatus;
import com.hackathon.food.entity.Role;
import com.hackathon.food.entity.User;
import com.hackathon.food.exception.ConflictException;
import com.hackathon.food.exception.FieldValidationException;
import com.hackathon.food.repo.FoodOrderRepository;
import com.hackathon.food.repo.MenuItemRepository;
import com.hackathon.food.repo.OrderEventRepository;
import com.hackathon.food.repo.UserRepository;

/** Phase 1: the server-side order board and paging, safe retries (idempotency keys), the order history and the restaurant's time zone. */
@SpringBootTest
@ActiveProfiles("test")
class OrderBoardAndRetryTest {

    static final String ADDRESS = "Flat 302, Green Park Apartments, Baner, Pune";
    static final String PHONE = "9876543210";
    static final String KEY = "6f1d3c52-8a7e-4b9c-9d11-0a2b3c4d5e6f";

    @Autowired FoodService food;
    @Autowired MenuItemRepository menu;
    @Autowired FoodOrderRepository orders;
    @Autowired OrderEventRepository events;
    @Autowired UserRepository users;

    MenuItem tikka;
    MenuItem naan;

    @BeforeEach
    void setUp() {
        orders.deleteAll();
        menu.deleteAll();
        users.deleteAll();
        users.save(new User("admin", "x", "Chef", Role.RESTAURANT_ADMIN));
        users.save(new User("admin2", "x", "Sous Chef", Role.RESTAURANT_ADMIN));
        for (int i = 1; i <= 6; i++) {
            users.save(new User("c" + i, "x", "Customer " + i, Role.CUSTOMER));
        }
        tikka = menu.save(new MenuItem("Paneer Tikka", "Tasty", MenuCategory.STARTERS, new BigDecimal("280.00"), true, true));
        naan = menu.save(new MenuItem("Butter Naan", "Tasty", MenuCategory.BREADS_AND_RICE, new BigDecimal("60.00"), true, true));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    static void loginAs(String username, Role role) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(username, "x",
                AuthorityUtils.createAuthorityList("ROLE_" + role.name())));
    }

    OrderRequest request(MenuItem dish, int quantity) {
        return new OrderRequest(List.of(new OrderLineBody(dish.getId(), quantity)), ADDRESS, PHONE, null);
    }

    OrderResponse place(String customer, MenuItem dish, int quantity) {
        loginAs(customer, Role.CUSTOMER);
        return food.place(customer, request(dish, quantity));
    }

    OrderResponse move(OrderResponse o, OrderStatus to) {
        loginAs("admin", Role.RESTAURANT_ADMIN);
        return food.changeStatus("admin", o.id(), to);
    }

    static <T> List<T> reversed(List<T> list) {
        List<T> copy = new java.util.ArrayList<>(list);
        java.util.Collections.reverse(copy);
        return copy;
    }

    List<Long> ids(List<OrderResponse> list) {
        return list.stream().map(OrderResponse::id).toList();
    }

    // ---------- the live board and paging happen in SQL ----------

    @Test
    void theLiveBoardAsksOnlyForOpenOrders() {
        OrderResponse delivered = place("c1", tikka, 2);
        OrderResponse cancelled = place("c2", tikka, 2);
        OrderResponse open1 = place("c3", tikka, 2);
        OrderResponse open2 = place("c4", naan, 5);
        move(move(move(move(delivered, OrderStatus.ACCEPTED), OrderStatus.PREPARING), OrderStatus.READY), OrderStatus.DELIVERED);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        food.cancelAsAdmin("admin", cancelled.id(), "Out of paneer");

        assertEquals(List.of(open2.id(), open1.id()), ids(food.adminOrders(null, true, null, null, null)));
        assertEquals(List.of(open2.id(), open1.id(), cancelled.id(), delivered.id()), ids(food.adminOrders(null, false, null, null, null)),
                "without openOnly every order is returned, open ones first, as before");
        assertEquals(List.of(delivered.id()), ids(food.adminOrders(OrderStatus.DELIVERED, true, null, null, null)), "an explicit status wins over openOnly");
    }

    @Test
    void historyIsReadOnePageAtATimeNewestFirst() {
        List<Long> placed = IntStream.rangeClosed(1, 5).mapToObj(i -> place("c" + i, tikka, 2)).map(OrderResponse::id).toList();
        loginAs("admin", Role.RESTAURANT_ADMIN);
        List<Long> newestFirst = reversed(placed);

        assertEquals(newestFirst.subList(0, 2), ids(food.adminOrders(null, false, null, 0, 2)));
        assertEquals(newestFirst.subList(2, 4), ids(food.adminOrders(null, false, null, 1, 2)));
        assertEquals(newestFirst.subList(4, 5), ids(food.adminOrders(null, false, null, 2, 2)));
        assertTrue(food.adminOrders(null, false, null, 3, 2).isEmpty());
        assertEquals(5, food.adminOrders(null, false, null, 0, null).size(), "a page without a size uses the default size");
    }

    @Test
    void pagingParametersAreValidated() {
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertEquals("size", assertThrows(FieldValidationException.class, () -> food.adminOrders(null, false, null, 0, 0)).getField());
        assertEquals("size", assertThrows(FieldValidationException.class, () -> food.adminOrders(null, false, null, 0, 101)).getField());
        assertEquals("page", assertThrows(FieldValidationException.class, () -> food.adminOrders(null, false, null, -1, 10)).getField());
    }

    @Test
    void searchStillFindsByNameNumberAndPhoneAndShrugsOffSqlWildcards() {
        OrderResponse a = place("c1", tikka, 2);
        place("c2", naan, 5);
        loginAs("admin", Role.RESTAURANT_ADMIN);
        assertEquals(List.of(a.id()), ids(food.adminOrders(null, false, "customer 1", null, null)));
        assertEquals(List.of(a.id()), ids(food.adminOrders(null, false, a.number(), null, null)));
        assertEquals(2, food.adminOrders(null, false, PHONE, null, null).size());
        assertTrue(food.adminOrders(null, false, "50%_off", null, null).isEmpty());
        assertEquals(2, food.adminOrders(null, false, "   ", null, null).size());
    }

    @Test
    void aCustomersOrdersCanBePagedToo() {
        List<Long> placed = IntStream.rangeClosed(1, 4).mapToObj(i -> place("c1", i % 2 == 0 ? tikka : naan, 2 + i)).map(OrderResponse::id).toList();
        loginAs("c1", Role.CUSTOMER);
        assertEquals(reversed(placed).subList(0, 3), ids(food.myOrders("c1", "all", 0, 3)));
        assertEquals(reversed(placed).subList(3, 4), ids(food.myOrders("c1", "all", 1, 3)));
        assertEquals(4, food.myOrders("c1", "active").size(), "the original two-argument form still returns everything");
    }

    // ---------- placing an order is safe to retry ----------

    @Test
    void aRetryWithTheSameKeyReturnsTheFirstOrderInsteadOfMakingAnother() {
        loginAs("c1", Role.CUSTOMER);
        FoodService.Placement first = food.place("c1", request(tikka, 2), KEY);
        FoodService.Placement retry = food.place("c1", request(tikka, 2), KEY);
        assertTrue(first.created());
        assertFalse(retry.created());
        assertEquals(first.order().id(), retry.order().id());
        assertEquals(1, orders.count());
    }

    @Test
    void theKeyStillWorksAfterTheOrderHasMovedOn() {
        loginAs("c1", Role.CUSTOMER);
        FoodService.Placement first = food.place("c1", request(tikka, 2), KEY);
        move(first.order(), OrderStatus.ACCEPTED);
        loginAs("c1", Role.CUSTOMER);
        FoodService.Placement retry = food.place("c1", request(tikka, 2), KEY);
        assertEquals(first.order().id(), retry.order().id());
        assertEquals("ACCEPTED", retry.order().status(), "the retry shows the order as it is now");
    }

    @Test
    void aKeyUsedForADifferentOrderIsRefused() {
        loginAs("c1", Role.CUSTOMER);
        food.place("c1", request(tikka, 2), KEY);
        assertThrows(ConflictException.class, () -> food.place("c1", request(naan, 9), KEY));
        assertEquals(1, orders.count());
    }

    @Test
    void keysBelongToACustomerAndAreChecked() {
        loginAs("c1", Role.CUSTOMER);
        FoodService.Placement mine = food.place("c1", request(tikka, 2), KEY);
        loginAs("c2", Role.CUSTOMER);
        FoodService.Placement theirs = food.place("c2", request(tikka, 2), KEY);
        assertTrue(theirs.created());
        assertFalse(mine.order().id().equals(theirs.order().id()));
        assertEquals("idempotencyKey", assertThrows(FieldValidationException.class, () -> food.place("c2", request(naan, 3), "short")).getField());
        assertEquals("idempotencyKey", assertThrows(FieldValidationException.class, () -> food.place("c2", request(naan, 3), "has spaces in it, not allowed")).getField());
    }

    @Test
    void withoutAKeyTheOldDoubleClickGuardStillApplies() {
        place("c1", tikka, 2);
        assertThrows(ConflictException.class, () -> place("c1", tikka, 2));
    }

    @Test
    void fiveParallelRetriesWithTheSameKeyMakeOneOrderAndAllGetIt() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(5);
        try {
            List<Callable<FoodService.Placement>> tasks = IntStream.range(0, 5).<Callable<FoodService.Placement>>mapToObj(i -> () -> {
                loginAs("c1", Role.CUSTOMER);
                try {
                    return food.place("c1", request(tikka, 2), KEY);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }).toList();
            List<FoodService.Placement> results = pool.invokeAll(tasks).stream().map(f -> {
                try {
                    return f.get();
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }
            }).toList();
            assertEquals(1, orders.count());
            assertEquals(1, results.stream().filter(FoodService.Placement::created).count());
            assertEquals(1, results.stream().map(r -> r.order().id()).distinct().count());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---------- who did what, and when ----------

    @Test
    void everyStepIsWrittenToTheOrderHistory() {
        OrderResponse o = place("c1", tikka, 2);
        move(move(move(move(o, OrderStatus.ACCEPTED), OrderStatus.PREPARING), OrderStatus.READY), OrderStatus.DELIVERED);
        List<OrderEvent> history = events.findByOrderIdOrderByIdAsc(o.id());
        assertEquals(List.of(OrderStatus.PLACED, OrderStatus.ACCEPTED, OrderStatus.PREPARING, OrderStatus.READY, OrderStatus.DELIVERED),
                history.stream().map(OrderEvent::getStatus).toList());
        assertEquals(List.of("c1", "admin", "admin", "admin", "admin"), history.stream().map(OrderEvent::getActor).toList());
        assertTrue(history.stream().map(OrderEvent::getOccurredAt).sorted().toList().equals(history.stream().map(OrderEvent::getOccurredAt).toList()));
    }

    @Test
    void cancellationsAreInTheHistoryWithWhoCancelled() {
        OrderResponse byCustomer = place("c1", tikka, 2);
        loginAs("c1", Role.CUSTOMER);
        food.cancelOwn("c1", byCustomer.id(), "Changed my mind");
        OrderResponse byKitchen = place("c2", naan, 5);
        loginAs("admin2", Role.RESTAURANT_ADMIN);
        food.cancelAsAdmin("admin2", byKitchen.id(), "Out of naan");

        List<OrderEvent> customerHistory = events.findByOrderIdOrderByIdAsc(byCustomer.id());
        OrderEvent customerLast = customerHistory.get(customerHistory.size() - 1);
        assertEquals(OrderStatus.CANCELLED, customerLast.getStatus());
        assertEquals("c1", customerLast.getActor());

        List<OrderEvent> kitchenHistory = events.findByOrderIdOrderByIdAsc(byKitchen.id());
        OrderEvent kitchenLast = kitchenHistory.get(kitchenHistory.size() - 1);
        assertEquals(OrderStatus.CANCELLED, kitchenLast.getStatus());
        assertEquals("admin2", kitchenLast.getActor());
    }

    // ---------- times carry the restaurant's zone ----------

    @Test
    void timesAreSentWithTheRestaurantsOffset() {
        OrderResponse o = place("c1", tikka, 2);
        assertEquals("+05:30", o.createdAt().getOffset().toString(), "app.timezone is Asia/Kolkata in the tests");
        assertTrue(Duration.between(o.createdAt(), OffsetDateTime.now()).abs().toSeconds() < 30, "and it is the actual moment, not shifted by the offset");
        OrderResponse accepted = move(o, OrderStatus.ACCEPTED);
        assertTrue(!accepted.acceptedAt().isBefore(o.createdAt()));
    }
}
