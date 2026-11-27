package com.springlab.lab.jpa;

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
 * The JPA lab commits real transactions (flush versus commit is part of what it teaches), so these tests are NOT
 * transactional: they create and delete their own user and check that no lab row survives a run.
 */
@AutoConfigureMockMvc
abstract class AbstractJpaLabTest extends AbstractSpringBootTest {

    static final List<String> LAB_TABLES = List.of("jpa_lab_enrollment", "jpa_lab_member", "jpa_lab_team", "jpa_lab_task",
            "jpa_lab_project", "jpa_lab_room", "jpa_lab_employee", "jpa_lab_department", "jpa_lab_profile", "jpa_lab_account",
            "jpa_lab_student", "jpa_lab_course");

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
        student = newUser("jpa_lab_student_" + System.nanoTime(), Role.STUDENT);
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
        String body = mockMvc.perform(post("/api/lab/jpa/experiments/{id}/run", experimentId).header("Authorization", bearer(user)))
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

    static long count(List<String> statements, String fragment) {
        return statements.stream().filter(s -> s.contains(fragment)).count();
    }

    long labRows() {
        long total = 0;
        for (String table : LAB_TABLES) {
            total += jdbc.queryForObject("select count(*) from " + table, Long.class);
        }
        return total;
    }
}
