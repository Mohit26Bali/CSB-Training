package com.hackathon.helpdesk.controller;

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

import com.hackathon.helpdesk.entity.Role;
import com.hackathon.helpdesk.entity.User;
import com.hackathon.helpdesk.repo.TicketCommentRepository;
import com.hackathon.helpdesk.repo.TicketRepository;
import com.hackathon.helpdesk.repo.UserRepository;
import com.hackathon.helpdesk.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired TicketRepository tickets;
    @Autowired TicketCommentRepository comments;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String agent;
    String employee;
    String other;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        comments.deleteAll();
        tickets.deleteAll();
        users.deleteAll();
        users.save(new User("agent", encoder.encode("agent123"), "Agent", Role.SUPPORT_AGENT));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        agent = "Bearer " + jwt.generateToken("agent", "SUPPORT_AGENT");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long create(String token, String title) throws Exception {
        return idOf(mvc.perform(post("/api/tickets").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"" + title + "\",\"description\":\"It is broken\",\"category\":\"NETWORK\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/tickets/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/agent/tickets").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/agent/dashboard").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(put("/api/agent/tickets/1/status").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"description\":\"y\",\"category\":\"NETWORK\",\"priority\":\"LOW\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/tickets/my").header("Authorization", agent)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tickets/meta").header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[0]").value("HARDWARE")).andExpect(jsonPath("$.priorities.length()").value(4));
    }

    @Test
    void ticketValidation() throws Exception {
        mvc.perform(post("/api/tickets").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.description").value("Description is required"))
                .andExpect(jsonPath("$.fieldErrors.category").value("Choose a category"))
                .andExpect(jsonPath("$.fieldErrors.priority").value("Choose a priority"));
        mvc.perform(post("/api/tickets").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\",\"description\":\"y\",\"category\":\"plumbing\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.category").exists());
    }

    @Test
    void employeeSeesOnlyOwnTicketsAndCannotOpenOthers() throws Exception {
        long id = create(employee, "VPN down");
        mvc.perform(get("/api/tickets/my").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN")).andExpect(jsonPath("$[0].slaState").value("ON_TRACK"));
        mvc.perform(get("/api/tickets/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/tickets/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/tickets/" + id).header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.ticket.title").value("VPN down"));
        mvc.perform(get("/api/tickets/999999").header("Authorization", agent)).andExpect(status().isNotFound());
    }

    @Test
    void agentWorksATicketFromOpenToClosed() throws Exception {
        long id = create(employee, "Laptop dead");
        mvc.perform(get("/api/agent/tickets?status=OPEN").header("Authorization", agent)).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(put("/api/agent/tickets/" + id + "/assign").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"emp\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.assignee").exists());
        mvc.perform(put("/api/agent/tickets/" + id + "/assign").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"assignee\":\"agent\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.assigneeUsername").value("agent"));
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").value("Choose a status"));
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"note\":\"x\"}")).andExpect(status().isConflict());
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("IN_PROGRESS"));
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.note").exists());
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"note\":\"Replaced the cable\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("Replaced the cable")).andExpect(jsonPath("$.slaState").value("MET"));
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CLOSED\"}")).andExpect(status().isOk());
        mvc.perform(put("/api/agent/tickets/" + id + "/status").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"IN_PROGRESS\"}")).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("cannot be reopened")));
        mvc.perform(get("/api/tickets/my?status=CLOSED").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].resolution").value("Replaced the cable"));
        mvc.perform(get("/api/agent/dashboard").header("Authorization", agent)).andExpect(jsonPath("$.closed").value(1));
        mvc.perform(get("/api/agent/agents").header("Authorization", agent)).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void commentsAndInternalNotes() throws Exception {
        long id = create(employee, "Slow wifi");
        mvc.perform(post("/api/tickets/" + id + "/comments").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.body").value("Write something first"));
        mvc.perform(post("/api/tickets/" + id + "/comments").header("Authorization", other).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"me too\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/" + id + "/comments").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Worse in the afternoon\",\"internal\":true}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tickets/" + id + "/comments").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"Worse in the afternoon\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorRole").value("EMPLOYEE"));
        mvc.perform(post("/api/tickets/" + id + "/comments").header("Authorization", agent).contentType(MediaType.APPLICATION_JSON)
                .content("{\"body\":\"AP is overloaded\",\"internal\":true}")).andExpect(status().isCreated()).andExpect(jsonPath("$.internal").value(true));
        mvc.perform(get("/api/tickets/" + id).header("Authorization", employee)).andExpect(jsonPath("$.comments.length()").value(1));
        mvc.perform(get("/api/tickets/" + id).header("Authorization", agent)).andExpect(jsonPath("$.comments.length()").value(2));
    }
}
