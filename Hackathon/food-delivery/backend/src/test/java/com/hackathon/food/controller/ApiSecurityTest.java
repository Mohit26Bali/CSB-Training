package com.hackathon.food.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired MenuItemRepository menu;
    @Autowired FoodOrderRepository orders;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String customer;
    String other;
    long tikka;
    long soldOut;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        orders.deleteAll();
        menu.deleteAll();
        users.deleteAll();
        users.save(new User("adm", encoder.encode("admin123"), "Chef", Role.RESTAURANT_ADMIN));
        users.save(new User("cust", encoder.encode("customer123"), "Customer", Role.CUSTOMER));
        users.save(new User("other", encoder.encode("customer123"), "Other", Role.CUSTOMER));
        tikka = menu.save(new MenuItem("Paneer Tikka", "Grilled paneer", MenuCategory.STARTERS, new BigDecimal("280.00"), true, true)).getId();
        soldOut = menu.save(new MenuItem("Rogan Josh", "Mutton", MenuCategory.MAINS, new BigDecimal("420.00"), false, false)).getId();
        admin = "Bearer " + jwt.generateToken("adm", "RESTAURANT_ADMIN");
        customer = "Bearer " + jwt.generateToken("cust", "CUSTOMER");
        other = "Bearer " + jwt.generateToken("other", "CUSTOMER");
    }

    private String order(long dish, int quantity) {
        return "{\"items\":[{\"menuItemId\":" + dish + ",\"quantity\":" + quantity + "}],\"address\":\"Flat 302, Green Park, Baner, Pune\",\"phone\":\"9876543210\"}";
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long place(String token, int quantity) throws Exception {
        return idOf(mvc.perform(post("/api/orders").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(order(tikka, quantity)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/menu")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/orders").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/menu").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/orders").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/my").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/menu").header("Authorization", admin)).andExpect(status().isOk());
        mvc.perform(get("/api/menu").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].imageKey").value("paneer-tikka"));
    }

    @Test
    void orderValidationShowsEveryProblem() throws Exception {
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.items").value("Add at least one dish to your order"))
                .andExpect(jsonPath("$.fieldErrors.address").value("Delivery address is required"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Phone number is required"));
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"items\":[{\"menuItemId\":" + tikka + ",\"quantity\":0}],\"address\":\"short\",\"phone\":\"12345\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['items[0].quantity']").value("Quantity must be at least 1"))
                .andExpect(jsonPath("$.fieldErrors.address").value("Address must be 10 to 200 characters"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Enter a 10 digit mobile number"));
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(soldOut, 1)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.items").value(org.hamcrest.Matchers.containsString("not available")));
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 21)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void orderingAndFollowingAnOrder() throws Exception {
        long id = place(customer, 2);
        mvc.perform(post("/api/orders").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(order(tikka, 2)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("just placed")));
        mvc.perform(get("/api/orders/my?scope=active").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].total").value(588.0)).andExpect(jsonPath("$[0].lines[0].name").value("Paneer Tikka"));
        mvc.perform(get("/api/orders/my?scope=sometimes").header("Authorization", customer)).andExpect(status().isBadRequest());
        mvc.perform(get("/api/orders/" + id).header("Authorization", customer)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PLACED"));
        mvc.perform(get("/api/orders/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/orders/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/999999").header("Authorization", customer)).andExpect(status().isNotFound());
    }

    @Test
    void theKitchenWorkflowAndCancellation() throws Exception {
        long id = place(customer, 2);
        mvc.perform(put("/api/admin/orders/" + id + "/status").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"ACCEPTED\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/orders/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").value("Choose a status"));
        mvc.perform(put("/api/admin/orders/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"READY\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("can only move to ACCEPTED")));
        for (String next : new String[] { "ACCEPTED", "PREPARING", "READY", "DELIVERED" }) {
            mvc.perform(put("/api/admin/orders/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + next + "\"}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value(next));
        }
        mvc.perform(put("/api/admin/orders/" + id + "/status").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DELIVERED\"}"))
                .andExpect(status().isConflict());
        long second = place(other, 3);
        mvc.perform(put("/api/admin/orders/" + second + "/cancel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put("/api/admin/orders/" + second + "/cancel").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Out of paneer\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.cancelledBy").value("adm"));
        mvc.perform(get("/api/orders/my?scope=past").header("Authorization", other)).andExpect(jsonPath("$[0].cancelReason").value("Out of paneer"));
        mvc.perform(get("/api/admin/orders?status=DELIVERED").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void customerCancellationAndMenuManagement() throws Exception {
        long id = place(customer, 2);
        mvc.perform(delete("/api/orders/" + id + "?reason=Mistake").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED")).andExpect(jsonPath("$.cancelReason").value("Mistake"));
        mvc.perform(delete("/api/orders/" + id).header("Authorization", customer)).andExpect(status().isConflict());
        mvc.perform(post("/api/admin/menu").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Category is required"))
                .andExpect(jsonPath("$.fieldErrors.price").value("Price is required"));
        mvc.perform(post("/api/admin/menu").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"paneer tikka\",\"category\":\"STARTERS\",\"price\":5}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.price").value("Price must be at least 10"));
        mvc.perform(post("/api/admin/menu").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"paneer tikka\",\"category\":\"STARTERS\",\"price\":250}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").value(org.hamcrest.Matchers.containsString("already a dish")));
        mvc.perform(post("/api/admin/menu").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Masala Dosa\",\"category\":\"MAINS\",\"price\":150,\"vegetarian\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true)).andExpect(jsonPath("$.imageKey").value("masala-dosa"));
        mvc.perform(get("/api/admin/menu").header("Authorization", admin)).andExpect(jsonPath("$.length()").value(3));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin)).andExpect(status().isOk()).andExpect(jsonPath("$.lastSevenDays.length()").value(7));
    }
}
