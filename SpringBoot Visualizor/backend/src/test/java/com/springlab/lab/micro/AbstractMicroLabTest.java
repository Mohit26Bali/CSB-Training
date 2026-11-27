package com.springlab.lab.micro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.repository.UserRepository;
import com.springlab.security.JwtService;
import com.springlab.support.AbstractSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The lab commits real transactions, so these tests are NOT transactional: they create and delete their own users and runs and check
 * that no lab row survives.
 */
@AutoConfigureMockMvc
abstract class AbstractMicroLabTest extends AbstractSpringBootTest {

    static final List<String> LAB_TABLES = List.of("micro_lab_saga_steps", "micro_lab_reservations", "micro_lab_outbox", "micro_lab_processed_events",
            "micro_lab_payments", "micro_lab_notifications", "micro_lab_orders", "micro_lab_stock", "micro_lab_registry", "micro_lab_products",
            "micro_lab_users");

    /** The node ids of the architecture diagram: every service, its database, the client, the simulated channel and the comparison monolith. */
    static final java.util.Set<String> DIAGRAM_NODES = java.util.Set.of("client", "broker", "monolith", "monolith-db", "gateway", "user", "product",
            "order", "inventory", "payment", "notification", "registry", "user-db", "product-db", "order-db", "inventory-db", "payment-db",
            "notification-db", "registry-db");

    @Autowired
    MockMvc mockMvc;
    @Autowired
    UserRepository userRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    JwtService jwtService;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    JdbcTemplate jdbc;

    final List<User> users = new ArrayList<>();
    User student;

    @BeforeEach
    void createUser() {
        student = newUser("micro_lab_student_" + System.nanoTime(), Role.STUDENT);
    }

    @AfterEach
    void deleteUsersAndRuns() {
        for (User user : users) {
            jdbc.update("delete from micro_lab_runs where owner = ?", user.getUsername());
        }
        userRepository.deleteAll(users);
    }

    User newUser(String username, Role role) {
        User user = userRepository.saveAndFlush(new User(username, username + "@example.com", passwordEncoder.encode("Sup3rSecret!"), role));
        users.add(user);
        return user;
    }

    String bearer(User user) {
        return "Bearer " + jwtService.issueFor(user).value();
    }

    ResultActions postJson(String path, User user, String body) throws Exception {
        var request = post(path).header("Authorization", bearer(user));
        if (body != null) {
            request = request.contentType("application/json").content(body);
        }
        return mockMvc.perform(request);
    }

    ResultActions getAs(String path, User user) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(user)));
    }

    JsonNode json(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    JsonNode createRun(User user, String experimentId) throws Exception {
        return createRun(user, experimentId, "{}");
    }

    JsonNode createRun(User user, String experimentId, String whatIfJson) throws Exception {
        return json(postJson("/api/lab/microservices/runs", user, "{\"experimentId\":\"" + experimentId + "\",\"whatIf\":" + whatIfJson + "}")
                .andExpect(status().isCreated()));
    }

    JsonNode execute(User user, String runId) throws Exception {
        return json(postJson("/api/lab/microservices/runs/" + runId + "/execute", user, null).andExpect(status().isOk()));
    }

    /** Create and execute in one go; returns the executed run view. */
    JsonNode run(User user, String experimentId, String whatIfJson) throws Exception {
        return execute(user, createRun(user, experimentId, whatIfJson).get("runId").asText());
    }

    JsonNode run(String experimentId) throws Exception {
        return run(student, experimentId, "{}");
    }

    long labRows() {
        long total = 0;
        for (String table : LAB_TABLES) {
            total += jdbc.queryForObject("select count(*) from " + table, Long.class);
        }
        return total;
    }

    static JsonNode step(JsonNode run, String stepId) {
        for (JsonNode step : run.get("result").get("steps")) {
            if (step.get("id").asText().equals(stepId)) {
                return step;
            }
        }
        throw new AssertionError("No step " + stepId);
    }

    static String evidence(JsonNode run, String stepId, String label) {
        for (JsonNode e : step(run, stepId).get("evidence")) {
            if (e.get("label").asText().equals(label)) {
                return e.get("value").asText();
            }
        }
        throw new AssertionError("No evidence '" + label + "' in step " + stepId);
    }

    static String evidenceStartingWith(JsonNode run, String stepId, String prefix) {
        for (JsonNode e : step(run, stepId).get("evidence")) {
            if (e.get("label").asText().startsWith(prefix)) {
                return e.get("value").asText();
            }
        }
        throw new AssertionError("No evidence starting '" + prefix + "' in step " + stepId);
    }

    /** Every (step id / label = value) of a run, for comparing two runs of the same experiment. */
    static java.util.List<String> allEvidence(JsonNode run) {
        java.util.List<String> out = new ArrayList<>();
        for (JsonNode s : run.get("result").get("steps")) {
            out.add(s.get("id").asText() + "|" + s.get("provenance").asText());
            for (JsonNode e : s.get("evidence")) {
                out.add(s.get("id").asText() + "/" + e.get("label").asText() + "=" + e.get("value").asText());
            }
        }
        return out;
    }

    JsonNode runWith(String experimentId, String whatIfJson) throws Exception {
        return run(student, experimentId, whatIfJson);
    }
}
