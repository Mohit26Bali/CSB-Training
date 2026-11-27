package com.springlab.coding;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Coding-practice API tests are not transactional (judging and persistence happen in different threads and transactions), so they create
 * their own users and remove everything those users wrote, including coding rows, before the users are deleted.
 */
@AutoConfigureMockMvc
public abstract class AbstractCodingTest extends AbstractSpringBootTest {

    protected static final String SLUG = "retry-with-bounded-backoff";
    protected static final String USER_PREFIX = "coding_test_";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected JdbcTemplate jdbc;

    protected final List<User> users = new ArrayList<>();
    protected User student;

    @BeforeEach
    void createStudent() {
        student = newUser(USER_PREFIX + "student_" + System.nanoTime(), Role.STUDENT);
    }

    @AfterEach
    void removeCodingRowsAndUsers() {
        for (User u : users) {
            purge(u);
        }
        userRepository.deleteAll(users);
    }

    /** Removes everything a test user wrote in the coding tables. */
    protected void purge(User u) {
        jdbc.update("update coding_progress set first_accepted_submission_id = null where user_id = ?", u.getId());
        jdbc.update("delete from coding_progress where user_id = ?", u.getId());
        jdbc.update("delete from coding_submissions where user_id = ?", u.getId()); // results cascade
    }

    protected User newUser(String username, Role role) {
        User user = userRepository.saveAndFlush(new User(username, username + "@example.com", passwordEncoder.encode("Sup3rSecret!"), role));
        users.add(user);
        return user;
    }

    protected String bearer(User user) {
        return "Bearer " + jwtService.issueFor(user).value();
    }

    protected ResultActions getAs(User user, String path) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", bearer(user)));
    }

    protected ResultActions postSource(User user, String path, String source) throws Exception {
        return mockMvc.perform(post(path).header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(java.util.Map.of("sourceCode", source))));
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        return objectMapper.readTree(actions.andReturn().getResponse().getContentAsString());
    }

    protected String text(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString();
    }

    protected long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    /** A syntactically valid solution; its content does not matter to tests that script the worker's answer. */
    protected static final String SOME_SOURCE = "import java.util.concurrent.Callable;\npublic final class RetryExecutor {\n"
            + "  public static <T> T execute(Callable<T> a, int m, long b, long c, Sleeper s) throws Exception { return a.call(); }\n}\n";
}
