package com.springlab.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Difficulty;
import com.springlab.entity.Question;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.repository.AttemptAnswerRepository;
import com.springlab.repository.AttemptRepository;
import com.springlab.repository.QuestionRepository;
import com.springlab.repository.TopicRepository;
import com.springlab.repository.UserRepository;
import com.springlab.lab.trace.TraceStore;
import com.springlab.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MockMvc tests over the full stack. Every test runs in a transaction that is rolled back, so tests leave no data
 * behind (including the seed questions, which are removed inside the transaction to give each test a clean slate).
 */
@AutoConfigureMockMvc
@Transactional
public abstract class AbstractIntegrationTest extends AbstractSpringBootTest {

    protected static final String PASSWORD = "Sup3rSecret!";

    @Autowired
    protected MockMvc mockMvc;
    @Autowired
    protected UserRepository userRepository;
    @Autowired
    protected PasswordEncoder passwordEncoder;
    @Autowired
    protected JwtService jwtService;
    @Autowired
    protected TopicRepository topicRepository;
    @Autowired
    protected QuestionRepository questionRepository;
    @Autowired
    protected AttemptRepository attemptRepository;
    @Autowired
    protected AttemptAnswerRepository attemptAnswerRepository;
    @Autowired
    protected TraceStore traceStore;

    @BeforeEach
    void cleanData() {
        // Child tables first. Rolled back after each test, so the seed data in the schema is untouched.
        attemptAnswerRepository.deleteAllInBatch();
        attemptRepository.deleteAllInBatch();
        questionRepository.deleteAllInBatch(); // options are removed by ON DELETE CASCADE
        userRepository.deleteAll();
        userRepository.flush();
    }

    /** A genuinely signed access token for the user, exactly as the login endpoint would issue it. */
    protected String bearer(User user) {
        return "Bearer " + jwtService.issueFor(user).value();
    }

    /** A single-use trace ticket bound to {@code owner}, obtained exactly as a browser would: through the real API. */
    protected String traceTicket(User owner) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/lab/traces/tickets").header("Authorization", bearer(owner)))
                .andExpect(status().isCreated()).andReturn();
        return new ObjectMapper().readTree(result.getResponse().getContentAsString()).get("ticket").asText();
    }

    protected User createUser(String username, Role role) {
        return userRepository.saveAndFlush(
                new User(username, username + "@example.com", passwordEncoder.encode(PASSWORD), role));
    }

    /**
     * Creates an active question with {@code optionCount} options named "Option 0", "Option 1", ...
     * where the option at {@code correctIndex} is the correct one.
     */
    protected Question createQuestion(String topicSlug, Difficulty difficulty, User author, String text,
                                      int optionCount, int correctIndex) {
        var topic = topicRepository.findBySlug(topicSlug).orElseThrow();
        Question question = new Question(topic, difficulty, text, "Explanation for: " + text, author);
        for (int i = 0; i < optionCount; i++) {
            question.addOption("Option " + i, i == correctIndex, i);
        }
        return questionRepository.saveAndFlush(question);
    }

    protected Question createQuestion(String topicSlug, Difficulty difficulty, String text) {
        return createQuestion(topicSlug, difficulty, null, text, 4, 1);
    }
}
