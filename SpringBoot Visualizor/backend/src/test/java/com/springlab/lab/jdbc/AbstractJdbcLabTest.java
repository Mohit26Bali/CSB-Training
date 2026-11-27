package com.springlab.lab.jdbc;

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

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The JDBC lab commits real transactions and opens real connections, so these tests are NOT transactional: they create and delete
 * their own user and check that no lab row survives a run.
 */
@AutoConfigureMockMvc
abstract class AbstractJdbcLabTest extends AbstractSpringBootTest {

    static final List<String> LAB_TABLES = List.of("jdbc_lab_orders", "jdbc_lab_transactions", "jdbc_lab_batch_records",
            "jdbc_lab_accounts", "jdbc_lab_products", "jdbc_lab_customers");

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
        student = newUser("jdbc_lab_student_" + System.nanoTime(), Role.STUDENT);
    }

    @AfterEach
    void deleteUsers() {
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

    JsonNode run(String experimentId) throws Exception {
        return run(experimentId, student);
    }

    JsonNode run(String experimentId, User user) throws Exception {
        String body = mockMvc.perform(post("/api/lab/jdbc/experiments/{id}/run", experimentId).header("Authorization", bearer(user)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    JsonNode getJson(String path) throws Exception {
        String body = mockMvc.perform(get(path).header("Authorization", bearer(student))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body);
    }

    static JsonNode step(JsonNode run, String stepId) {
        for (JsonNode step : run.get("steps")) {
            if (step.get("id").asText().equals(stepId)) {
                return step;
            }
        }
        throw new AssertionError("no step " + stepId + " in " + run.get("experimentId").asText());
    }

    static String evidence(JsonNode run, String stepId, String label) {
        for (JsonNode e : step(run, stepId).get("evidence")) {
            if (e.get("label").asText().equals(label)) {
                return e.get("value").asText();
            }
        }
        throw new AssertionError("no evidence '" + label + "' in step " + stepId);
    }

    static List<String> sql(JsonNode step) {
        List<String> statements = new ArrayList<>();
        step.get("sql").forEach(s -> statements.add(s.get("sql").asText().toLowerCase()));
        return statements;
    }

    static List<String> triggers(JsonNode step) {
        List<String> values = new ArrayList<>();
        step.get("sql").forEach(s -> values.add(s.get("trigger").asText()));
        return values;
    }

    long labRows() {
        long total = 0;
        for (String table : LAB_TABLES) {
            total += jdbc.queryForObject("select count(*) from " + table, Long.class);
        }
        return total;
    }
}
