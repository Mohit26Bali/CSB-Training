package com.hackathon.maintenance.controller;

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

import com.hackathon.maintenance.entity.Category;
import com.hackathon.maintenance.entity.Role;
import com.hackathon.maintenance.entity.Technician;
import com.hackathon.maintenance.entity.User;
import com.hackathon.maintenance.repo.MaintenanceRequestRepository;
import com.hackathon.maintenance.repo.TechnicianRepository;
import com.hackathon.maintenance.repo.UserRepository;
import com.hackathon.maintenance.security.JwtService;

@SpringBootTest
@ActiveProfiles("test")
class ApiSecurityTest {

    @Autowired WebApplicationContext context;
    @Autowired UserRepository users;
    @Autowired MaintenanceRequestRepository requests;
    @Autowired TechnicianRepository technicians;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService jwt;

    MockMvc mvc;
    String manager;
    String resident;
    String other;
    long technicianId;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        requests.deleteAll();
        technicians.deleteAll();
        users.deleteAll();
        users.save(new User("mgr", encoder.encode("manager123"), "Manager", Role.FACILITY_MANAGER));
        users.save(new User("res", encoder.encode("resident123"), "Res", Role.RESIDENT));
        users.save(new User("other", encoder.encode("resident123"), "Other", Role.RESIDENT));
        technicianId = technicians.save(new Technician("Ramesh", Category.PLUMBING, "+91 98450 11201")).getId();
        manager = "Bearer " + jwt.generateToken("mgr", "FACILITY_MANAGER");
        resident = "Bearer " + jwt.generateToken("res", "RESIDENT");
        other = "Bearer " + jwt.generateToken("other", "RESIDENT");
    }

    private long idOf(String json) {
        return Long.parseLong(json.replaceAll(".*?\"id\":(\\d+).*", "$1"));
    }

    private long raise(String token, String location) throws Exception {
        return idOf(mvc.perform(post("/api/requests").header("Authorization", token).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"PLUMBING\",\"description\":\"Leak\",\"location\":\"" + location + "\",\"priority\":\"HIGH\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void json(long id, String token, String path, String body, int expected) throws Exception {
        mvc.perform(put("/api/manager/requests/" + id + path).header("Authorization", token).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected));
    }

    @Test
    void anonymousIs401() throws Exception {
        mvc.perform(get("/api/requests/my")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void rolesAreSeparated() throws Exception {
        mvc.perform(get("/api/manager/requests").header("Authorization", resident)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/dashboard").header("Authorization", resident)).andExpect(status().isForbidden());
        mvc.perform(get("/api/manager/technicians").header("Authorization", resident)).andExpect(status().isForbidden());
        json(1, resident, "/assign", "{\"technicianId\":1}", 403);
        json(1, resident, "/status", "{\"status\":\"IN_PROGRESS\"}", 403);
        mvc.perform(post("/api/requests").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"PLUMBING\",\"description\":\"x\",\"location\":\"y\",\"priority\":\"LOW\"}")).andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/my").header("Authorization", manager)).andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/meta").header("Authorization", resident)).andExpect(jsonPath("$.categories.length()").value(8))
                .andExpect(jsonPath("$.priorities[3]").value("EMERGENCY"));
    }

    @Test
    void requestValidation() throws Exception {
        mvc.perform(post("/api/requests").header("Authorization", resident).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.category").value("Choose a category"))
                .andExpect(jsonPath("$.fieldErrors.description").value("Please describe the problem"))
                .andExpect(jsonPath("$.fieldErrors.location").value("Say where the problem is"))
                .andExpect(jsonPath("$.fieldErrors.priority").value("Choose a priority"));
        mvc.perform(post("/api/requests").header("Authorization", resident).contentType(MediaType.APPLICATION_JSON)
                .content("{\"category\":\"magic\",\"description\":\"d\",\"location\":\"l\",\"priority\":\"LOW\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.category").exists());
    }

    @Test
    void residentsOnlyEverSeeTheirOwnRequests() throws Exception {
        long id = raise(resident, "Block A, 101");
        mvc.perform(get("/api/requests/my").header("Authorization", resident)).andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN")).andExpect(jsonPath("$[0].aging").value("FRESH"));
        mvc.perform(get("/api/requests/my").header("Authorization", other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/requests/" + id).header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(get("/api/requests/" + id).header("Authorization", resident)).andExpect(status().isOk());
        mvc.perform(get("/api/requests/" + id).header("Authorization", manager)).andExpect(status().isOk());
        mvc.perform(get("/api/requests/999999").header("Authorization", manager)).andExpect(status().isNotFound());
    }

    @Test
    void managerAssignsStartsResolvesAndCloses() throws Exception {
        long id = raise(resident, "Block B, 402");
        json(id, manager, "/assign", "{}", 400);
        json(id, manager, "/assign", "{\"technicianId\":999999}", 400);
        json(id, manager, "/status", "{\"status\":\"ASSIGNED\"}", 400);
        json(id, manager, "/status", "{\"status\":\"IN_PROGRESS\"}", 409);
        mvc.perform(put("/api/manager/requests/" + id + "/assign").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"technicianId\":" + technicianId + "}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED")).andExpect(jsonPath("$.technicianName").value("Ramesh"));
        json(id, manager, "/status", "{\"status\":\"IN_PROGRESS\"}", 200);
        mvc.perform(put("/api/manager/requests/" + id + "/status").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.notes").value("Resolution notes are required when resolving a request"));
        mvc.perform(put("/api/manager/requests/" + id + "/status").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"RESOLVED\",\"notes\":\"Replaced the joint\"}")).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolutionNotes").value("Replaced the joint")).andExpect(jsonPath("$.aging").doesNotExist());
        mvc.perform(get("/api/requests/my").header("Authorization", resident)).andExpect(jsonPath("$[0].resolutionNotes").value("Replaced the joint"));
        json(id, manager, "/status", "{\"status\":\"CLOSED\"}", 200);
        json(id, manager, "/status", "{\"status\":\"IN_PROGRESS\"}", 409);
        json(id, manager, "/assign", "{\"technicianId\":" + technicianId + "}", 409);
        mvc.perform(get("/api/manager/dashboard").header("Authorization", manager)).andExpect(jsonPath("$.closed").value(1))
                .andExpect(jsonPath("$.byCategory.length()").value(8));
    }

    @Test
    void techniciansAndTheQueue() throws Exception {
        raise(resident, "Block C, 12");
        mvc.perform(get("/api/manager/requests?category=PLUMBING&priority=HIGH&aging=fresh&search=block c").header("Authorization", manager))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/manager/requests?status=RESOLVED").header("Authorization", manager)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post("/api/manager/technicians").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.trade").value("Choose a trade"));
        mvc.perform(post("/api/manager/technicians").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"ramesh\",\"trade\":\"PLUMBING\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value(org.hamcrest.Matchers.containsString("already on the list")));
        mvc.perform(post("/api/manager/technicians").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mohan\",\"trade\":\"garden\",\"phone\":\"abc\"}")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").value("Enter a valid phone number"));
        mvc.perform(post("/api/manager/technicians").header("Authorization", manager).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Mohan\",\"trade\":\"garden\",\"phone\":\"+91 98450 22222\"}")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.trade").value("GARDEN"));
        mvc.perform(put("/api/manager/technicians/" + technicianId + "/active?value=false").header("Authorization", manager))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        mvc.perform(get("/api/manager/technicians").header("Authorization", manager)).andExpect(jsonPath("$.length()").value(2));
    }
}
