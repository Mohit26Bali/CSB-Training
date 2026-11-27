package com.hackathon.hostel.controller;

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

import com.hackathon.hostel.entity.Role;
import com.hackathon.hostel.entity.Room;
import com.hackathon.hostel.entity.RoomType;
import com.hackathon.hostel.entity.User;
import com.hackathon.hostel.repo.RoomAllocationRepository;
import com.hackathon.hostel.repo.RoomRepository;
import com.hackathon.hostel.repo.RoomRequestRepository;
import com.hackathon.hostel.repo.UserRepository;
import com.hackathon.hostel.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired RoomRepository rooms;
    @Autowired RoomRequestRepository requests;
    @Autowired RoomAllocationRepository allocations;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String warden;
    String student;
    String other;
    long roomId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        allocations.deleteAll();
        requests.deleteAll();
        rooms.deleteAll();
        users.deleteAll();
        users.save(new User("warden", encoder.encode("admin123"), "Warden", Role.HOSTEL_ADMIN));
        users.save(new User("stu", encoder.encode("student123"), "Stu", Role.STUDENT));
        users.save(new User("other", encoder.encode("student123"), "Other", Role.STUDENT));
        roomId = rooms.save(new Room("A-101", 1, RoomType.SINGLE, 1)).getId();
        warden = "Bearer " + jwt.generateToken("warden", "HOSTEL_ADMIN");
        student = "Bearer " + jwt.generateToken("stu", "STUDENT");
        other = "Bearer " + jwt.generateToken("other", "STUDENT");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long ask(String token) throws Exception {
        return idOf(mvc.perform(post("/api/requests").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"note\":\"please\"}")).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/rooms")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/rooms").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", student)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/rooms").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"X\",\"floor\":1,\"type\":\"SINGLE\",\"capacity\":1}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/requests").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/allocation/my").header("Authorization", warden)).andExpect(status().isForbidden());
        mvc.perform(get("/api/rooms").header("Authorization", student)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/rooms/types").header("Authorization", student)).andExpect(jsonPath("$[0]").value("SINGLE"));
    }

    @Test
    void roomValidationAndDuplicates() throws Exception {
        mvc.perform(post("/api/admin/rooms").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.number").value("Room number is required"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity is required"));
        mvc.perform(post("/api/admin/rooms").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"B-1\",\"floor\":1,\"type\":\"SINGLE\",\"capacity\":0}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1"));
        mvc.perform(post("/api/admin/rooms").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"a-101\",\"floor\":1,\"type\":\"SINGLE\",\"capacity\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.number").value("Room A-101 already exists"));
        mvc.perform(post("/api/admin/rooms").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"number\":\"b-202\",\"floor\":2,\"type\":\"double\",\"capacity\":2}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.number").value("B-202")).andExpect(jsonPath("$.bedsLeft").value(2));
    }

    @Test
    void requestApproveAndViewAllocationEndToEnd() throws Exception {
        mvc.perform(get("/api/allocation/my").header("Authorization", student)).andExpect(status().isNoContent());
        long id = ask(student);
        mvc.perform(post("/api/requests").header("Authorization", student).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/requests").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content("{\"preferredType\":\"castle\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.type").exists());
        mvc.perform(get("/api/admin/requests?status=PENDING").header("Authorization", warden))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].queuePosition").value(1));

        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.roomId").value("Choose a room to allocate"));
        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", student).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":" + roomId + "}")).andExpect(status().isForbidden());
        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":" + roomId + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED")).andExpect(jsonPath("$.roomNumber").value("A-101"));
        mvc.perform(put("/api/admin/requests/" + id + "/approve").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":" + roomId + "}")).andExpect(status().isConflict());

        mvc.perform(get("/api/allocation/my").header("Authorization", student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.roomNumber").value("A-101")).andExpect(jsonPath("$.active").value(true));
        mvc.perform(get("/api/rooms").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/admin/rooms").header("Authorization", warden))
                .andExpect(jsonPath("$[0].occupied").value(1)).andExpect(jsonPath("$[0].occupants[0]").value("Stu"));

        long other2 = ask(other);
        mvc.perform(put("/api/admin/requests/" + other2 + "/approve").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"roomId\":" + roomId + "}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("full")));

        long allocationId = idOf(mvc.perform(get("/api/admin/allocations").header("Authorization", warden)).andReturn().getResponse().getContentAsString());
        mvc.perform(put("/api/admin/allocations/" + allocationId + "/vacate").header("Authorization", warden)).andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
        mvc.perform(put("/api/admin/allocations/" + allocationId + "/vacate").header("Authorization", warden)).andExpect(status().isConflict());
        mvc.perform(get("/api/allocation/my").header("Authorization", student)).andExpect(status().isNoContent());
    }

    @Test
    void rejectionNeedsAReasonAndCancelIsOwnerOnly() throws Exception {
        long id = ask(student);
        mvc.perform(put("/api/admin/requests/" + id + "/reject").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.reason").value("A reason is required when rejecting"));
        mvc.perform(delete("/api/requests/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/requests/999999").header("Authorization", student)).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/requests/" + id + "/reject").header("Authorization", warden).contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"No space\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(delete("/api/requests/" + id).header("Authorization", student)).andExpect(status().isConflict());
        mvc.perform(get("/api/requests/my").header("Authorization", student))
                .andExpect(jsonPath("$[0].decisionReason").value("No space"));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", warden)).andExpect(jsonPath("$.rooms").value(1))
                .andExpect(jsonPath("$.pendingRequests").value(0));
    }
}
