package com.hackathon.visitors.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.hackathon.visitors.entity.Role;
import com.hackathon.visitors.entity.User;
import com.hackathon.visitors.repo.UserRepository;
import com.hackathon.visitors.repo.VisitRepository;
import com.hackathon.visitors.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired VisitRepository visits;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String guard;
    String employee;
    String other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        visits.deleteAll();
        users.deleteAll();
        users.save(new User("guard", encoder.encode("guard123"), "Guard", Role.SECURITY_ADMIN));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        guard = "Bearer " + jwt.generateToken("guard", "SECURITY_ADMIN");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private String body(String name, String phone, LocalDate date) {
        return "{\"visitorName\":\"" + name + "\",\"phone\":\"" + phone + "\",\"purpose\":\"Meeting\",\"expectedDate\":\"" + date + "\"}";
    }

    private long invite(String token, String name, String phone, LocalDate date) throws Exception {
        return idOf(mvc.perform(post("/api/visitors").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body(name, phone, date)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/visitors/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/security/visitors").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/security/stats").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(put("/api/security/visitors/1/check-in").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(put("/api/security/visitors/1/check-out").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/visitors").header("Authorization", guard).contentType(MediaType.APPLICATION_JSON).content(body("A", "9845011201", LocalDate.now())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/visitors/my").header("Authorization", guard)).andExpect(status().isForbidden());
    }

    @Test
    void invitationValidation() throws Exception {
        mvc.perform(post("/api/visitors").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.visitorName").value("Visitor name is required"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("Visitor phone is required"))
                .andExpect(jsonPath("$.fieldErrors.purpose").value("Purpose of the visit is required"))
                .andExpect(jsonPath("$.fieldErrors.expectedDate").value("Expected date is required"));
        mvc.perform(post("/api/visitors").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(body("A", "abc", LocalDate.now())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.phone").value("Enter a valid phone number"));
        mvc.perform(post("/api/visitors").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(body("A", "9845011201", LocalDate.now().minusDays(1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.expectedDate").value("The expected date cannot be in the past"));
    }

    @Test
    void employeesSeeOnlyTheirOwnInvitations() throws Exception {
        long id = invite(employee, "Rakesh", "+91 98450 11201", LocalDate.now());
        mvc.perform(get("/api/visitors/my").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("EXPECTED")).andExpect(jsonPath("$[0].passCode").value(org.hamcrest.Matchers.startsWith("VP-")))
                .andExpect(jsonPath("$[0].phone").value("+919845011201"));
        mvc.perform(get("/api/visitors/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/visitors").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(body("Again", "9198450 11201", LocalDate.now())))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/visitors/" + id).header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(body("X", "9845011299", LocalDate.now())))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/visitors/" + id).header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(body("Rakesh B", "9845011299", LocalDate.now().plusDays(1))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.visitorName").value("Rakesh B")).andExpect(jsonPath("$.dueToday").value(false));
        mvc.perform(delete("/api/visitors/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/visitors/" + id).header("Authorization", employee)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(delete("/api/visitors/" + id).header("Authorization", employee)).andExpect(status().isConflict());
        mvc.perform(delete("/api/visitors/999999").header("Authorization", employee)).andExpect(status().isNotFound());
    }

    @Test
    void securityWorksTheGateFromListToCheckOut() throws Exception {
        long id = invite(employee, "Rakesh", "9845011201", LocalDate.now());
        long future = invite(employee, "Later", "9845011202", LocalDate.now().plusDays(2));
        mvc.perform(get("/api/security/visitors").header("Authorization", guard)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].canCheckIn").value(true));
        mvc.perform(get("/api/security/visitors?date=" + LocalDate.now().plusDays(2)).header("Authorization", guard)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/security/visitors?search=later").header("Authorization", guard)).andExpect(jsonPath("$[0].visitorName").value("Later"));
        String pass = mvc.perform(get("/api/security/visitors").header("Authorization", guard)).andReturn().getResponse().getContentAsString()
                .replaceAll(".*?\"passCode\":\"(VP-\\d+)\".*", "$1");
        mvc.perform(get("/api/security/pass/" + pass.toLowerCase()).header("Authorization", guard)).andExpect(status().isOk())
                .andExpect(jsonPath("$.visitorName").value("Rakesh"));
        mvc.perform(get("/api/security/pass/VP-000000").header("Authorization", guard)).andExpect(status().isNotFound());

        mvc.perform(put("/api/security/visitors/" + id + "/check-out").header("Authorization", guard)).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not been checked in")));
        mvc.perform(put("/api/security/visitors/" + future + "/check-in").header("Authorization", guard)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("not today")));
        mvc.perform(put("/api/security/visitors/" + id + "/check-in").header("Authorization", guard)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_IN")).andExpect(jsonPath("$.checkedInBy").value("guard"))
                .andExpect(jsonPath("$.canCheckOut").value(true));
        mvc.perform(put("/api/security/visitors/" + id + "/check-in").header("Authorization", guard)).andExpect(status().isConflict());
        mvc.perform(get("/api/visitors/my?status=CHECKED_IN").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/visitors/" + id).header("Authorization", employee)).andExpect(status().isConflict());
        mvc.perform(put("/api/security/visitors/" + id + "/check-out").header("Authorization", guard)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHECKED_OUT"));
        mvc.perform(put("/api/security/visitors/" + id + "/check-out").header("Authorization", guard)).andExpect(status().isConflict());
        mvc.perform(put("/api/security/visitors/999999/check-in").header("Authorization", guard)).andExpect(status().isNotFound());
        mvc.perform(get("/api/security/stats").header("Authorization", guard)).andExpect(jsonPath("$.checkedOutToday").value(1))
                .andExpect(jsonPath("$.upcomingNextSevenDays").value(1)).andExpect(jsonPath("$.lastSevenDays.length()").value(7));
    }
}
