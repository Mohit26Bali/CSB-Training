package com.hackathon.warranty.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

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

import com.hackathon.warranty.entity.Product;
import com.hackathon.warranty.entity.ProductCategory;
import com.hackathon.warranty.entity.Role;
import com.hackathon.warranty.entity.User;
import com.hackathon.warranty.repo.ClaimRepository;
import com.hackathon.warranty.repo.ProductRepository;
import com.hackathon.warranty.repo.UserRepository;
import com.hackathon.warranty.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired ClaimRepository claims;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String agent;
    String customer;
    String other;
    long productId;
    long expiredId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        claims.deleteAll();
        products.deleteAll();
        users.deleteAll();
        users.save(new User("agent", encoder.encode("agent123"), "Agent", Role.SERVICE_AGENT));
        User c = users.save(new User("cust", encoder.encode("customer123"), "Cust", Role.CUSTOMER));
        users.save(new User("other", encoder.encode("customer123"), "Other", Role.CUSTOMER));
        productId = products.save(new Product(c, "Laptop", "Acme", ProductCategory.LAPTOP, "SER-1", LocalDate.now().minusMonths(2), 12)).getId();
        expiredId = products.save(new Product(c, "Old phone", "Acme", ProductCategory.MOBILE, "SER-2", LocalDate.now().minusMonths(30), 12)).getId();
        agent = "Bearer " + jwt.generateToken("agent", "SERVICE_AGENT");
        customer = "Bearer " + jwt.generateToken("cust", "CUSTOMER");
        other = "Bearer " + jwt.generateToken("other", "CUSTOMER");
    }

    private long fileClaim(String token, long product) throws Exception {
        String body = mvc.perform(post("/api/claims").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + product + ",\"issueDescription\":\"Screen is black\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/products/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/agent/claims").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/agent/summary").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/my").header("Authorization", agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/claims/my").header("Authorization", agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/products/categories").header("Authorization", agent)).andExpect(status().isOk());
    }

    @Test
    void productsShowTheWarrantyAndArePrivate() throws Exception {
        mvc.perform(get("/api/products/my").header("Authorization", customer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/products/my?search=old").header("Authorization", customer))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].warrantyActive").value(false));
        mvc.perform(get("/api/products/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void registeringAProductValidatesEachField() throws Exception {
        mvc.perform(post("/api/products").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"brand\":\"\",\"category\":\"\",\"serialNumber\":\"x\",\"warrantyMonths\":99}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Product name is required"))
                .andExpect(jsonPath("$.fieldErrors.serialNumber").value("Serial number must be 3 to 40 characters"))
                .andExpect(jsonPath("$.fieldErrors.purchaseDate").value("Purchase date is required"))
                .andExpect(jsonPath("$.fieldErrors.warrantyMonths").value("Warranty can be at most 60 months"));
        String ok = "{\"name\":\"Fridge\",\"brand\":\"LG\",\"category\":\"APPLIANCE\",\"serialNumber\":\"fr-9\",\"purchaseDate\":\"" + LocalDate.now().minusMonths(1) + "\",\"warrantyMonths\":24}";
        mvc.perform(post("/api/products").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.serialNumber").value("FR-9"));
        mvc.perform(post("/api/products").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(ok))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.serialNumber").exists());
    }

    @Test
    void claimRulesExpiredDuplicateAndOwnership() throws Exception {
        mvc.perform(post("/api/claims").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + expiredId + ",\"issueDescription\":\"Broken\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("expired")));
        fileClaim(customer, productId);
        mvc.perform(post("/api/claims").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"issueDescription\":\"Again\"}")).andExpect(status().isConflict());
        mvc.perform(post("/api/claims").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":" + productId + ",\"issueDescription\":\"Not mine\"}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/claims").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.productId").exists())
                .andExpect(jsonPath("$.fieldErrors.issueDescription").exists());
        mvc.perform(get("/api/products/my?search=laptop").header("Authorization", customer))
                .andExpect(jsonPath("$[0].hasActiveClaim").value(true));
    }

    @Test
    void agentWorkflowFromPendingToCompleted() throws Exception {
        long id = fileClaim(customer, productId);
        String path = "/api/agent/claims/" + id;
        mvc.perform(put(path + "/start-repair").header("Authorization", agent)).andExpect(status().isConflict());
        mvc.perform(put(path + "/reject").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put(path + "/approve").header("Authorization", agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.decidedBy").value("agent"));
        mvc.perform(put(path + "/approve").header("Authorization", agent)).andExpect(status().isConflict());
        mvc.perform(put(path + "/start-repair").header("Authorization", agent))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_REPAIR"));
        mvc.perform(put(path + "/complete").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"resolutionNote\":\"Board replaced\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETED")).andExpect(jsonPath("$.resolutionNote").value("Board replaced"));
        mvc.perform(get("/api/claims/my").header("Authorization", customer))
                .andExpect(jsonPath("$[0].status").value("COMPLETED")).andExpect(jsonPath("$[0].resolutionNote").value("Board replaced"));
        mvc.perform(get("/api/claims/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/agent/summary").header("Authorization", agent))
                .andExpect(jsonPath("$.length()").value(5));
    }

    @Test
    void customersCannotDecideClaims() throws Exception {
        long id = fileClaim(customer, productId);
        mvc.perform(put("/api/agent/claims/" + id + "/approve").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/agent/claims/" + id + "/complete").header("Authorization", customer)).andExpect(status().isForbidden());
    }

    @Test
    void registrationAlwaysCreatesACustomer() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"SERVICE_AGENT\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("CUSTOMER"));
    }
}
