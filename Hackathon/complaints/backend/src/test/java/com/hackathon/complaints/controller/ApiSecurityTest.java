package com.hackathon.complaints.controller;

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

import com.hackathon.complaints.entity.Role;
import com.hackathon.complaints.entity.User;
import com.hackathon.complaints.repo.ComplaintEventRepository;
import com.hackathon.complaints.repo.ComplaintRepository;
import com.hackathon.complaints.repo.UserRepository;
import com.hackathon.complaints.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired ComplaintRepository complaints;
    @Autowired ComplaintEventRepository events;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String agent;
    String customer;
    String other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        events.deleteAll();
        complaints.deleteAll();
        users.deleteAll();
        users.save(new User("agent", encoder.encode("agent123"), "Agent", Role.SUPPORT_AGENT));
        users.save(new User("cust", encoder.encode("customer123"), "Cust", Role.CUSTOMER));
        users.save(new User("other", encoder.encode("customer123"), "Other", Role.CUSTOMER));
        agent = "Bearer " + jwt.generateToken("agent", "SUPPORT_AGENT");
        customer = "Bearer " + jwt.generateToken("cust", "CUSTOMER");
        other = "Bearer " + jwt.generateToken("other", "CUSTOMER");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long file(String token, String subject) throws Exception {
        return idOf(mvc.perform(post("/api/complaints").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"" + subject + "\",\"description\":\"It went wrong\",\"category\":\"BILLING\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private String move(long id, String token, String body) throws Exception {
        return mvc.perform(put("/api/agent/complaints/" + id + "/status").header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/complaints/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/agent/complaints").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(get("/api/agent/statistics").header("Authorization", customer)).andExpect(status().isForbidden());
        mvc.perform(put("/api/agent/complaints/1/status").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"note\":\"x\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/complaints").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"x\",\"description\":\"y\",\"category\":\"BILLING\",\"priority\":\"LOW\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/complaints/my").header("Authorization", agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/complaints/meta").header("Authorization", customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0]").value("BILLING")).andExpect(jsonPath("$.priorities[3]").value("URGENT"));
    }

    @Test
    void complaintValidation() throws Exception {
        mvc.perform(post("/api/complaints").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.subject").value("Subject is required"))
                .andExpect(jsonPath("$.fieldErrors.description").value("Please describe what went wrong"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Choose a category"))
                .andExpect(jsonPath("$.fieldErrors.priority").value("Choose a priority"));
        mvc.perform(post("/api/complaints").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subject\":\"x\",\"description\":\"y\",\"category\":\"weather\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.category").exists());
    }

    @Test
    void customersOnlyEverSeeTheirOwnComplaints() throws Exception {
        long id = file(customer, "Charged twice");
        mvc.perform(get("/api/complaints/my").header("Authorization", customer)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
        mvc.perform(get("/api/complaints/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/complaints/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/complaints/" + id).header("Authorization", customer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.timeline[0].type").value("FILED"));
        mvc.perform(get("/api/complaints/" + id).header("Authorization", agent)).andExpect(status().isOk());
        mvc.perform(get("/api/complaints/999999").header("Authorization", agent)).andExpect(status().isNotFound());
    }

    @Test
    void anAgentResolvesWithNotesAndTheCustomerSeesThemAndRates() throws Exception {
        long id = file(customer, "Refund missing");
        mvc.perform(put("/api/agent/complaints/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").value("Choose a status"));
        mvc.perform(put("/api/agent/complaints/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"note\":\"x\"}")).andExpect(status().isConflict());
        move(id, agent, "{\"status\":\"IN_PROGRESS\"}");
        mvc.perform(put("/api/agent/complaints/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.note").value("Resolution notes are required when resolving a complaint"));
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":5}")).andExpect(status().isConflict());
        mvc.perform(put("/api/agent/complaints/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"note\":\"Refund processed\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("Refund processed")).andExpect(jsonPath("$.canRate").value(true));
        mvc.perform(get("/api/complaints/my").header("Authorization", customer)).andExpect(jsonPath("$[0].resolution").value("Refund processed"));

        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.rating").value("Choose a rating from 1 to 5"));
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":9}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":4,\"comment\":\"Thanks\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.canRate").value(false));
        mvc.perform(post("/api/complaints/" + id + "/feedback").header("Authorization", customer).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":1}")).andExpect(status().isConflict());

        move(id, agent, "{\"status\":\"CLOSED\"}");
        mvc.perform(put("/api/agent/complaints/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be reopened")));
        mvc.perform(get("/api/agent/statistics").header("Authorization", agent)).andExpect(jsonPath("$.closed").value(1))
                .andExpect(jsonPath("$.averageRating").value(4.0));
    }

    @Test
    void assignmentAndTheQueue() throws Exception {
        long id = file(customer, "Assign me");
        mvc.perform(get("/api/agent/complaints?assignee=unassigned&priority=HIGH&category=BILLING").header("Authorization", agent))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/agent/complaints/" + id + "/assign").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"cust\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.assignee").exists());
        mvc.perform(put("/api/agent/complaints/" + id + "/assign").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"agent\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUsername").value("agent"));
        mvc.perform(get("/api/agent/complaints?assignee=me").header("Authorization", agent)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/agent/agents").header("Authorization", agent)).andExpect(jsonPath("$[0].activeComplaints").value(1));
    }
}
