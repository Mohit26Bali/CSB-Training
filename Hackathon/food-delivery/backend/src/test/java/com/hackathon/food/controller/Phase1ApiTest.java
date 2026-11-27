package com.hackathon.food.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.hackathon.food.entity.MenuCategory;
import com.hackathon.food.entity.MenuItem;
import com.hackathon.food.entity.Role;
import com.hackathon.food.entity.User;
import com.hackathon.food.repo.FoodOrderRepository;
import com.hackathon.food.repo.MenuItemRepository;
import com.hackathon.food.repo.UserRepository;
import com.hackathon.food.security.JwtService;

/** Phase 1 over HTTP: retry-safe checkout, the cancel endpoint with a body, the paged board, the health check and request ids, and login throttling. */
@SpringBootTest
@ActiveProfiles("test")
class Phase1ApiTest {

    static final String KEY = "0b6a5c1e-77d4-4f0e-bd2a-6f5c1d2e3a4b";

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired MenuItemRepository menu;
    @Autowired FoodOrderRepository orders;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;
    @Autowired com.hackathon.food.security.RequestIdFilter requestIdFilter;   // servlet filters are not part of MockMvc unless added

    MockMvc mvc;
    String admin;
    String customer;
    long tikka;
    long naan;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(requestIdFilter).apply(springSecurity()).build();
        orders.deleteAll();
        menu.deleteAll();
        users.deleteAll();
        users.save(new User("adm", encoder.encode("admin123"), "Chef", Role.RESTAURANT_ADMIN));
        users.save(new User("cust", encoder.encode("customer123"), "Customer", Role.CUSTOMER));
        tikka = menu.save(new MenuItem("Paneer Tikka", "Grilled paneer", MenuCategory.STARTERS, new BigDecimal("280.00"), true, true)).getId();
        naan = menu.save(new MenuItem("Butter Naan", "Bread", MenuCategory.BREADS_AND_RICE, new BigDecimal("60.00"), true, true)).getId();
        admin = "Bearer " + jwt.generateToken("adm", "RESTAURANT_ADMIN");
        customer = "Bearer " + jwt.generateToken("cust", "CUSTOMER");
    }

    private String order(long dish, int quantity) {
        return "{\"items\":[{\"menuItemId\":" + dish + ",\"quantity\":" + quantity + "}],\"address\":\"Flat 302, Green Park, Baner, Pune\",\"phone\":\"9876543210\"}";
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    @Test
    void aRetriedCheckoutAnswers200WithTheSameOrderAndTheFirstAnswers201() throws Exception {
        String first = mvc.perform(post("/api/orders").header("Authorization", customer).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String retry = mvc.perform(post("/api/orders").header("Authorization", customer).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        org.junit.jupiter.api.Assertions.assertEquals(idOf(first), idOf(retry));
        org.junit.jupiter.api.Assertions.assertEquals(1, orders.count());
        mvc.perform(post("/api/orders").header("Authorization", customer).header("Idempotency-Key", KEY)
                .contentType(MediaType.APPLICATION_JSON).content(order(naan, 9))).andExpect(status().isConflict());
        mvc.perform(post("/api/orders").header("Authorization", customer).header("Idempotency-Key", "x")
                .contentType(MediaType.APPLICATION_JSON).content(order(naan, 9))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.idempotencyKey").exists());
    }

    @Test
    void anOrderWithoutAKeyStillAnswers201() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PLACED"));
    }

    @Test
    void orderTimesCarryTheRestaurantsOffset() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andExpect(jsonPath("$.createdAt").value(matchesPattern(".*\\+05:30$")));
    }

    @Test
    void theReasonCanTravelInTheBodyAndTheOldQueryFormStillWorks() throws Exception {
        String a = mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/orders/" + idOf(a) + "/cancel").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Ordered by mistake\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelReason").value("Ordered by mistake"));
        String b = mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(naan, 5)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(delete("/api/orders/" + idOf(b)).param("reason", "Changed my mind").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelReason").value("Changed my mind"));
        String c = mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 3)))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/orders/" + idOf(c) + "/cancel").header("Authorization", customer)).andExpect(status().isOk());
        mvc.perform(post("/api/orders/" + idOf(c) + "/cancel").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
    }

    @Test
    void theBoardCanBeFilteredAndPagedAndRejectsBadValues() throws Exception {
        for (int quantity = 2; quantity <= 6; quantity++) {
            mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, quantity)))
                    .andExpect(status().isCreated());
        }
        mvc.perform(get("/api/admin/orders").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(5));
        mvc.perform(get("/api/admin/orders?scope=open&size=2&page=0").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/admin/orders?scope=open&size=2&page=2").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/orders?scope=history").header("Authorization", admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.scope").exists());
        mvc.perform(get("/api/admin/orders?size=500").header("Authorization", admin)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.size").exists());
        mvc.perform(get("/api/orders/my?size=2&page=1").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/orders/my?size=0").header("Authorization", customer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/orders?scope=open").header("Authorization", customer)).andExpect(status().isForbidden());
    }

    @Test
    void healthIsPublicAndEveryResponseCarriesARequestId() throws Exception {
        mvc.perform(get("/api/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[A-Za-z0-9_-]{8,64}")));
        mvc.perform(get("/api/menu")).andExpect(status().isUnauthorized()).andExpect(header().exists("X-Request-Id"));
        mvc.perform(get("/api/health").header("X-Request-Id", "my-trace-id-12345")).andExpect(header().string("X-Request-Id", "my-trace-id-12345"));
        mvc.perform(get("/api/health").header("X-Request-Id", "bad id with spaces\"")).andExpect(header().string("X-Request-Id", matchesPattern("[A-Za-z0-9_-]{8,64}")));
    }

    @Test
    void wrongPasswordsLockOnlyTheGuessersAddress() throws Exception {
        String wrong = "{\"username\":\"adm\",\"password\":\"nope-nope\"}";
        for (int i = 0; i < 5; i++) {
            mvc.perform(post("/api/auth/login").with(from("203.0.113.9")).contentType(MediaType.APPLICATION_JSON).content(wrong)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(from("203.0.113.9")).contentType(MediaType.APPLICATION_JSON).content(wrong))
                .andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.message").value(containsString("Too many")));
        mvc.perform(post("/api/auth/login").with(from("203.0.113.9")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"adm\",\"password\":\"admin123\"}"))
                .andExpect(status().isTooManyRequests());
        mvc.perform(post("/api/auth/login").with(from("198.51.100.4")).contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"adm\",\"password\":\"admin123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("RESTAURANT_ADMIN"));
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    @Test
    void theKitchenStillMovesOrdersAlongAndSeesThemInTheHistoryTabs() throws Exception {
        String a = mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andReturn().getResponse().getContentAsString();
        for (String next : new String[] { "ACCEPTED", "PREPARING", "READY", "DELIVERED" }) {
            mvc.perform(put("/api/admin/orders/" + idOf(a) + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"status\":\"" + next + "\"}")).andExpect(status().isOk());
        }
        mvc.perform(get("/api/admin/orders?status=DELIVERED&size=10").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/admin/orders?scope=open").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin)).andExpect(jsonPath("$.ordersToday").value(1)).andExpect(jsonPath("$.openOrders").value(0));
    }
}
