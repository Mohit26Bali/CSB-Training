package com.springlab.lab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.springlab.entity.Role;
import com.springlab.entity.User;
import com.springlab.lab.trace.LifecycleTraceFilter;
import com.springlab.repository.UserRepository;
import com.springlab.security.JwtService;
import com.springlab.support.AbstractSpringBootTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Deliberately NOT @Transactional: several users, on separate threads and separate database transactions, trace requests
 * at the same moment. Each trace must contain only its own request (collector isolation) and belong only to its own user
 * (ownership under concurrency).
 */
@AutoConfigureMockMvc
class TraceConcurrencyIntegrationTest extends AbstractSpringBootTest {

    private static final int USERS = 6;
    private static final int ROUNDS = 4;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<User> users = new ArrayList<>();

    @BeforeEach
    void createUsers() {
        for (int i = 0; i < USERS; i++) {
            users.add(userRepository.saveAndFlush(new User("conc_user_" + i, "conc_user_" + i + "@example.com",
                    passwordEncoder.encode("Sup3rSecret!"), Role.STUDENT)));
        }
    }

    @AfterEach
    void removeUsers() {
        userRepository.deleteAll(users);
    }

    private String bearer(User user) {
        return "Bearer " + jwtService.issueFor(user).value();
    }

    private record Result(User user, String traceId, JsonNode ownTrace, int othersStatus) {
    }

    @Test
    void simultaneousTracedRequestsStayIsolatedAndOwnedByTheirOwnUsers() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(USERS);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                CyclicBarrier startTogether = new CyclicBarrier(USERS);
                List<Callable<Result>> tasks = new ArrayList<>();
                for (User user : users) {
                    tasks.add(() -> {
                        String auth = bearer(user);
                        MvcResult ticket = mockMvc.perform(post("/api/lab/traces/tickets").header("Authorization", auth))
                                .andReturn();
                        String ticketValue = objectMapper.readTree(ticket.getResponse().getContentAsString())
                                .get("ticket").asText();
                        startTogether.await(10, TimeUnit.SECONDS); // all traced requests overlap in time
                        MvcResult traced = mockMvc.perform(get("/api/auth/me").header("Authorization", auth)
                                .header(LifecycleTraceFilter.REQUEST_HEADER, ticketValue)).andReturn();
                        String id = traced.getResponse().getHeader(LifecycleTraceFilter.RESPONSE_HEADER);
                        MvcResult own = mockMvc.perform(get("/api/lab/traces/" + id).header("Authorization", auth))
                                .andReturn();
                        // The same trace, requested by the NEXT user in the ring, must be refused.
                        User other = users.get((users.indexOf(user) + 1) % USERS);
                        MvcResult cross = mockMvc.perform(get("/api/lab/traces/" + id).header("Authorization",
                                bearer(other))).andReturn();
                        return new Result(user, id, objectMapper.readTree(own.getResponse().getContentAsString()),
                                cross.getResponse().getStatus());
                    });
                }

                List<Result> results = new ArrayList<>();
                for (Future<Result> f : pool.invokeAll(tasks, 60, TimeUnit.SECONDS)) {
                    results.add(f.get());
                }

                assertThat(results.stream().map(Result::traceId).distinct().count()).as("round %d: distinct traces", round)
                        .isEqualTo(USERS);
                for (Result r : results) {
                    String who = r.user().getUsername();
                    JsonNode trace = r.ownTrace();
                    assertThat(r.othersStatus()).as("%s: another user reading this trace", who).isEqualTo(404);
                    // Collector isolation: this trace holds exactly this request's two statements, for this user only.
                    assertThat(trace.get("sqlCount").asInt()).as("%s: SQL count (no leakage between threads)", who).isEqualTo(2);
                    assertThat(trace.get("status").asInt()).isEqualTo(200);
                    assertThat(trace.toString()).contains(who);
                    for (User other : users) {
                        if (other != r.user()) {
                            assertThat(trace.toString()).as("%s: no trace of %s", who, other.getUsername())
                                    .doesNotContain(other.getUsername());
                        }
                    }
                }
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
