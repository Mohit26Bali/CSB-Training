package com.hackathon.procurement.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.hackathon.procurement.entity.Role;
import com.hackathon.procurement.entity.User;
import com.hackathon.procurement.repo.PurchaseRequestRepository;
import com.hackathon.procurement.repo.UserRepository;
import com.hackathon.procurement.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired PurchaseRequestRepository requests;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String manager;
    String employee;
    String other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        requests.deleteAll();
        users.deleteAll();
        users.save(new User("pm", encoder.encode("manager123"), "Procurement", Role.PROCUREMENT_MANAGER));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        manager = "Bearer " + jwt.generateToken("pm", "PROCUREMENT_MANAGER");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private static String json(String item, String category, String quantity, String cost, String why) {
        return String.format("{\"itemName\":\"%s\",\"category\":\"%s\",\"quantity\":%s,\"estimatedUnitCost\":%s,\"justification\":\"%s\"}",
                item, category, quantity, cost, why);
    }

    private long create(String token) throws Exception {
        String body = mvc.perform(post("/api/requests").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content(json("Monitor", "HARDWARE", "2", "9000.50", "Dual screens"))).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/requests/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/manager/requests").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/dashboard").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/my").header("Authorization", manager)).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content(json("Monitor", "HARDWARE", "1", "10", "x"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/categories").header("Authorization", manager)).andExpect(status().isOk());
    }

    @Test
    void createdRequestHasATotalAndIsPrivateToItsOwner() throws Exception {
        long id = create(employee);
        mvc.perform(get("/api/requests/my").header("Authorization", employee))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].estimatedTotal").value(18001.0)).andExpect(jsonPath("$[0].status").value("PENDING"));
        mvc.perform(get("/api/requests/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/manager/requests/" + id + "/approve").header("Authorization", employee)).andExpect(status().isForbidden());
    }

    @Test
    void validationErrorsAreReportedPerField() throws Exception {
        mvc.perform(post("/api/requests").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(json("", "", "0", "-5", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.itemName").value("Item is required"))
                .andExpect(jsonPath("$.fieldErrors.quantity").value("Quantity must be at least 1"))
                .andExpect(jsonPath("$.fieldErrors.estimatedUnitCost").value("Estimated cost must be greater than zero"))
                .andExpect(jsonPath("$.fieldErrors.justification").value("Justification is required"));
        mvc.perform(post("/api/requests").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(json("Chair", "YACHTS", "1", "100", "Back pain")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.category").exists());
    }

    @Test
    void fullLifecycleApproveOrderDeliver() throws Exception {
        long id = create(employee);
        String path = "/api/manager/requests/" + id;
        mvc.perform(put(path + "/order").header("Authorization", manager)).andExpect(status().isConflict()); // not approved yet
        mvc.perform(put(path + "/approve").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.decidedBy").value("pm"));
        mvc.perform(put(path + "/approve").header("Authorization", manager)).andExpect(status().isConflict());
        mvc.perform(put(path + "/order").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderReference\":\"PO-1\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ORDERED")).andExpect(jsonPath("$.orderReference").value("PO-1"));
        mvc.perform(put(path + "/deliver").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DELIVERED"));
        mvc.perform(get("/api/manager/dashboard").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.spentThisMonth").value(18001.0))
                .andExpect(jsonPath("$.byStatus.length()").value(5));
    }

    @Test
    void rejectedRequestsNeedAReasonAndCannotBeOrdered() throws Exception {
        long id = create(employee);
        String path = "/api/manager/requests/" + id;
        mvc.perform(put(path + "/reject").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").exists());
        mvc.perform(put(path + "/reject").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Over budget\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.rejectionReason").value("Over budget"));
        mvc.perform(put(path + "/order").header("Authorization", manager)).andExpect(status().isConflict());
    }

    @Test
    void registrationAlwaysCreatesAnEmployee() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"newbie\",\"password\":\"password1\",\"fullname\":\"New B\",\"role\":\"PROCUREMENT_MANAGER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("EMPLOYEE"));
    }
}
