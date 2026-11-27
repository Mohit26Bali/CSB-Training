package com.hackathon.event.controller;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

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

import com.hackathon.event.entity.Event;
import com.hackathon.event.entity.Role;
import com.hackathon.event.entity.User;
import com.hackathon.event.repo.EventRepository;
import com.hackathon.event.repo.RegistrationRepository;
import com.hackathon.event.repo.UserRepository;
import com.hackathon.event.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired EventRepository events;
    @Autowired RegistrationRepository registrations;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String admin;
    String employee;
    String other;
    long eventId;
    long fullId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        registrations.deleteAll();
        events.deleteAll();
        users.deleteAll();
        users.save(new User("adm", encoder.encode("admin123"), "Admin", Role.EVENT_ADMIN));
        users.save(new User("emp", encoder.encode("employee123"), "Emp", Role.EMPLOYEE));
        users.save(new User("other", encoder.encode("employee123"), "Other", Role.EMPLOYEE));
        eventId = events.save(published("Cloud Talk", 5)).getId();
        fullId = events.save(published("Tiny Workshop", 1)).getId();
        admin = "Bearer " + jwt.generateToken("adm", "EVENT_ADMIN");
        employee = "Bearer " + jwt.generateToken("emp", "EMPLOYEE");
        other = "Bearer " + jwt.generateToken("other", "EMPLOYEE");
    }

    private Event published(String title, int capacity) {
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        Event e = new Event(title, "Workshop", "Room 1", "Details", start, start.plusHours(2), capacity, "adm");
        e.publish();
        return e;
    }

    private String register(long event, boolean waitlist) {
        return "{\"eventId\":" + event + ",\"joinWaitlist\":" + waitlist + "}";
    }

    private String eventJson(String title, String category, int capacity, LocalDateTime start) {
        return String.format("{\"title\":\"%s\",\"category\":\"%s\",\"venue\":\"Hall\",\"description\":\"Details\","
                + "\"startsAt\":\"%s\",\"endsAt\":\"%s\",\"capacity\":%d}", title, category, start, start.plusHours(2), capacity);
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/events")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/admin/events").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", employee)).andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/events").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("X", "Workshop", 5, LocalDateTime.now().plusDays(2)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/registrations/my").header("Authorization", admin)).andExpect(status().isForbidden());
        mvc.perform(post("/api/registrations").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(register(eventId, false))).andExpect(status().isForbidden());
        mvc.perform(get("/api/events").header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/events/categories").header("Authorization", employee)).andExpect(jsonPath("$[0]").value("Tech Talk"));
    }

    @Test
    void registrationFlowWithFullEventsAndWaitlist() throws Exception {
        mvc.perform(post("/api/registrations").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.eventId").value("Choose an event"));
        mvc.perform(post("/api/registrations").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(register(fullId, false)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("CONFIRMED"));
        mvc.perform(post("/api/registrations").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(register(fullId, false)))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/registrations").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(register(fullId, false)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("waitlist")));
        mvc.perform(post("/api/registrations").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(register(fullId, true)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("WAITLISTED")).andExpect(jsonPath("$.waitlistPosition").value(1));
        mvc.perform(get("/api/registrations/my").header("Authorization", other))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].waitlistPosition").value(1));
        mvc.perform(get("/api/events?search=tiny").header("Authorization", employee))
                .andExpect(jsonPath("$[0].myStatus").value("CONFIRMED")).andExpect(jsonPath("$[0].spotsLeft").value(0))
                .andExpect(jsonPath("$[0].waitlisted").value(1));
    }

    @Test
    void cancellationIsOwnerOnlyAndPromotesTheWaitlist() throws Exception {
        String created = mvc.perform(post("/api/registrations").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON)
                .content(register(fullId, false))).andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(post("/api/registrations").header("Authorization", other).contentType(MediaType.APPLICATION_JSON).content(register(fullId, true)));
        mvc.perform(delete("/api/registrations/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/registrations/999999").header("Authorization", employee)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/registrations/" + id).header("Authorization", employee)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mvc.perform(get("/api/registrations/my").header("Authorization", other)).andExpect(jsonPath("$[0].status").value("CONFIRMED"));
        mvc.perform(delete("/api/registrations/" + id).header("Authorization", employee)).andExpect(status().isConflict());
    }

    @Test
    void adminCreatesValidatesPublishesAndSeesParticipants() throws Exception {
        mvc.perform(post("/api/admin/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").value("Title is required"))
                .andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity is required"));
        mvc.perform(post("/api/admin/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("Old", "Workshop", 5, LocalDateTime.now().minusDays(1))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.startsAt").exists());
        mvc.perform(post("/api/admin/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("Zero", "Workshop", 0, LocalDateTime.now().plusDays(2))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.capacity").value("Capacity must be at least 1"));
        mvc.perform(post("/api/admin/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("Odd", "Nope", 5, LocalDateTime.now().plusDays(2))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.category").exists());

        String created = mvc.perform(post("/api/admin/events").header("Authorization", admin).contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("New Meetup", "Social", 10, LocalDateTime.now().plusDays(5))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long id = Long.parseLong(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));
        mvc.perform(get("/api/events?search=meetup").header("Authorization", employee)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/registrations").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(register(id, false)))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/events/" + id + "/publish").header("Authorization", admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"));
        mvc.perform(put("/api/admin/events/" + id + "/publish").header("Authorization", admin)).andExpect(status().isConflict());
        mvc.perform(post("/api/registrations").header("Authorization", employee).contentType(MediaType.APPLICATION_JSON).content(register(id, false)))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/events/" + id + "/participants").header("Authorization", admin))
                .andExpect(jsonPath("$.length()").value(1)).andExpect(jsonPath("$[0].username").value("emp"));
        mvc.perform(get("/api/admin/events/999999/participants").header("Authorization", admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", admin)).andExpect(jsonPath("$.totalEvents").value(3))
                .andExpect(jsonPath("$.confirmedSeats").value(1));
    }
}
