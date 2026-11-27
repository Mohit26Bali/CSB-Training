package com.hackathon.parking.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.hackathon.parking.entity.ParkingSlot;
import com.hackathon.parking.entity.Role;
import com.hackathon.parking.entity.SlotStatus;
import com.hackathon.parking.entity.User;
import com.hackathon.parking.repo.ParkingAllocationRepository;
import com.hackathon.parking.repo.ParkingRequestRepository;
import com.hackathon.parking.repo.SlotRepository;
import com.hackathon.parking.repo.UserRepository;
import com.hackathon.parking.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired SlotRepository slots;
    @Autowired ParkingRequestRepository requests;
    @Autowired ParkingAllocationRepository allocations;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String employee;
    String other;
    long slotId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        allocations.deleteAll();
        requests.deleteAll();
        slots.deleteAll();
        users.deleteAll();
        users.save(new User("adm", encoder.encode("admin123"), "Admin", Role.PARKING_ADMIN));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        slotId = slots.save(new ParkingSlot("F1-01", 1, SlotStatus.AVAILABLE)).getId();
        admin = "Bearer " + jwt.generateToken("adm", "PARKING_ADMIN");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long ask(String token, String vehicle) throws Exception {
        return idOf(mvc.perform(post("/api/parking/requests").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNumber\":\"" + vehicle + "\",\"note\":\"please\"}")).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/slots")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/slots").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/slots").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"X\",\"floor\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/parking/requests").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNumber\":\"MH12AB1234\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/parking/allocation/my").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(get("/api/slots").header("Authorization", employee)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/slots/floors").header("Authorization", employee)).andExpect(jsonPath("$[0]").value(1));
    }

    @Test
    void slotAndRequestValidation() throws Exception {
        mvc.perform(post("/api/admin/slots").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").value("Slot code is required"))
                .andExpect(jsonPath("$.fieldErrors.floor").value("Floor is required"));
        mvc.perform(post("/api/admin/slots").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"f1-01\",\"floor\":1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.code").value("Slot F1-01 already exists"));
        mvc.perform(post("/api/admin/slots").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"f2-01\",\"floor\":2}")).andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("F2-01"));

        mvc.perform(post("/api/parking/requests").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.vehicleNumber").value("Vehicle number is required"));
        mvc.perform(post("/api/parking/requests").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNumber\":\"12345678\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.vehicleNumber").value(org.hamcrest.Matchers.containsString("valid vehicle number")));
        mvc.perform(post("/api/parking/requests").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNumber\":\"MH12AB1234\",\"preferredFloor\":-1}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.preferredFloor").exists());
    }

    @Test
    void requestApproveViewAndReleaseEndToEnd() throws Exception {
        mvc.perform(get("/api/parking/allocation/my").header("Authorization", employee)).andExpect(status().isNoContent());
        long id = ask(employee, "MH12AB1234");
        mvc.perform(post("/api/parking/requests").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"vehicleNumber\":\"MH12AB9999\"}")).andExpect(status().isConflict());
        mvc.perform(get("/api/admin/requests?status=PENDING").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].queuePosition").value(1))
                .andExpect(jsonPath("$[0].vehicleNumber").value("MH12AB1234"));

        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.slotId").value("Choose a slot to allocate"));
        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slotId + "}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slotId + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.slotCode").value("F1-01"));
        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slotId + "}")).andExpect(status().isConflict());

        mvc.perform(get("/api/parking/allocation/my").header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.slotCode").value("F1-01")).andExpect(jsonPath("$.vehicleNumber").value("MH12AB1234"));
        mvc.perform(get("/api/slots").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/slots").header("Authorization", admin))
                .andExpect(jsonPath("$[0].occupied").value(true)).andExpect(jsonPath("$[0].occupantName").value("Emp"));

        long otherId = ask(other, "MH14CD5678");
        mvc.perform(put("/api/admin/requests/" + otherId + "/approve").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"slotId\":" + slotId + "}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("taken")));

        long allocationId = idOf(mvc.perform(get("/api/admin/allocations").header("Authorization", admin)).andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/admin/allocations/" + allocationId + "/release").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.releasedBy").value("adm"));
        mvc.perform(put("/api/admin/allocations/" + allocationId + "/release").header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(get("/api/parking/allocation/my").header("Authorization", employee)).andExpect(status().isNoContent());
    }

    @Test
    void rejectionNeedsAReasonAndCancelIsOwnerOnly() throws Exception {
        long id = ask(employee, "MH12AB1234");
        mvc.perform(put("/api/admin/requests/" + id + "/reject").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").value("A reason is required when rejecting"));
        mvc.perform(delete("/api/parking/requests/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/parking/requests/999999").header("Authorization", employee)).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/requests/" + id + "/reject").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"Lot full\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(delete("/api/parking/requests/" + id).header("Authorization", employee)).andExpect(status().isConflict());
        mvc.perform(get("/api/parking/requests/my").header("Authorization", employee)).andExpect(jsonPath("$[0].decisionReason").value("Lot full"));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin)).andExpect(jsonPath("$.slots").value(1))
                .andExpect(jsonPath("$.pendingRequests").value(0));
    }
}
